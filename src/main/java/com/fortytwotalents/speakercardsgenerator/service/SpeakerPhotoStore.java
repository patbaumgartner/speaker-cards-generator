package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.StorageConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for speaker profile pictures: downloads them during import and
 * reads them back for the web UI and the banner renderer.
 *
 * <p>
 * Photos are looked up in two places, in order:
 * <ol>
 * <li>the configured runtime directory ({@code app.storage.photo-dir}) — where imports
 * write;</li>
 * <li>the classpath under {@code /static/images/speaker/} — so photos bundled into the
 * image or checked in by hand keep working.</li>
 * </ol>
 *
 * <h2>Download hardening</h2> Image URLs come from third-party CFP systems and are
 * therefore untrusted. Downloads are restricted to {@code http}/{@code https}, capped at
 * {@link StorageConfig#getMaxPhotoSize()}, limited to a small number of redirect hops
 * (each hop re-validated), and refused for loopback/link-local/private destinations
 * unless explicitly allowed.
 *
 * <p>
 * Note: the address check resolves the host name and inspects the answers. A hostile DNS
 * server could still return a public address for the check and a private one for the
 * actual connection (DNS rebinding). Defeating that requires pinning the connection to
 * the validated address, which the JDK HTTP client does not expose; the check stops the
 * realistic attack (a literal private IP or a name that simply points at one).
 */
@Component
public class SpeakerPhotoStore {

	private static final Logger log = LoggerFactory.getLogger(SpeakerPhotoStore.class);

	/** Accepted image extensions, in probe order. */
	private static final List<String> EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");

	private static final Map<String, MediaType> CONTENT_TYPES = Map.of("jpg", MediaType.IMAGE_JPEG, "jpeg",
			MediaType.IMAGE_JPEG, "png", MediaType.IMAGE_PNG, "gif", MediaType.IMAGE_GIF, "webp",
			MediaType.parseMediaType("image/webp"));

	/** Maps a response content type back to the extension we store the file under. */
	private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of("image/jpeg", "jpg", "image/jpg", "jpg",
			"image/png", "png", "image/gif", "gif", "image/webp", "webp");

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

	private static final String CLASSPATH_PREFIX = "/static/images/speaker/";

	private static final int MAX_REDIRECTS = 3;

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final StorageConfig config;

	private final HttpClient httpClient;

	public SpeakerPhotoStore(StorageConfig config) {
		this.config = config;
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			// Redirects are followed manually so that every hop can be re-validated.
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	}

	/** A stored profile picture together with the media type it should be served as. */
	public record Photo(byte[] content, MediaType contentType) {
	}

	/**
	 * Finds the profile picture for a speaker.
	 * @param speakerId speaker UUID
	 * @return the photo, or empty when none has been imported
	 */
	public Optional<Photo> find(UUID speakerId) {
		if (speakerId == null) {
			return Optional.empty();
		}
		for (String extension : EXTENSIONS) {
			Path file = photoDirectory().resolve(speakerId + "." + extension);
			if (Files.isRegularFile(file)) {
				try {
					return Optional.of(new Photo(Files.readAllBytes(file), contentType(extension)));
				}
				catch (IOException ex) {
					log.warn("Could not read profile picture {}", file, ex);
				}
			}
		}
		for (String extension : EXTENSIONS) {
			String resource = CLASSPATH_PREFIX + speakerId + "." + extension;
			try (InputStream in = getClass().getResourceAsStream(resource)) {
				if (in != null) {
					return Optional.of(new Photo(in.readAllBytes(), contentType(extension)));
				}
			}
			catch (IOException ex) {
				log.warn("Could not read bundled profile picture {}", resource, ex);
			}
		}
		return Optional.empty();
	}

	/** Returns {@code true} when a profile picture is already stored for the speaker. */
	public boolean exists(UUID speakerId) {
		if (speakerId == null) {
			return false;
		}
		for (String extension : EXTENSIONS) {
			if (Files.isRegularFile(photoDirectory().resolve(speakerId + "." + extension))) {
				return true;
			}
			if (getClass().getResource(CLASSPATH_PREFIX + speakerId + "." + extension) != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Downloads a profile picture unless one is already stored. Never throws: a failed
	 * photo download must not abort an otherwise successful speaker import.
	 * @param speakerId speaker UUID, used as the file name
	 * @param sourceUrl absolute {@code http}/{@code https} URL of the image
	 * @return {@code true} when a new file was written
	 */
	public boolean download(UUID speakerId, String sourceUrl) {
		if (speakerId == null || sourceUrl == null || sourceUrl.isBlank()) {
			return false;
		}
		if (exists(speakerId)) {
			log.debug("Profile picture already stored for speaker {}, skipping download", speakerId);
			return false;
		}
		try {
			return fetchAndStore(speakerId, sourceUrl.trim());
		}
		catch (Exception ex) {
			log.warn("Could not download profile picture for speaker {} from {}: {}", speakerId, sourceUrl,
					ex.getMessage());
			return false;
		}
	}

	private boolean fetchAndStore(UUID speakerId, String sourceUrl) throws IOException, InterruptedException {
		URI uri = URI.create(sourceUrl);
		for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
			validateDestination(uri);
			HttpRequest request = HttpRequest.newBuilder().uri(uri).timeout(TIMEOUT).GET().build();
			HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				if (isRedirect(response.statusCode())) {
					uri = uri.resolve(response.headers()
						.firstValue("location")
						.orElseThrow(() -> new IOException("redirect without Location header")));
					continue;
				}
				if (response.statusCode() != 200) {
					throw new IOException("HTTP " + response.statusCode());
				}
				String extension = resolveExtension(response, uri);
				byte[] content = readAtMost(body, config.getMaxPhotoSize().toBytes());
				write(speakerId, extension, content);
				log.info("Stored profile picture for speaker {} ({} bytes)", speakerId, content.length);
				return true;
			}
		}
		throw new IOException("too many redirects");
	}

	private void write(UUID speakerId, String extension, byte[] content) throws IOException {
		Path directory = photoDirectory();
		Files.createDirectories(directory);
		Path target = directory.resolve(speakerId + "." + extension);
		// Write to a temporary file first so a crashed download never leaves a
		// half-written image that later probes would happily serve.
		Path temp = Files.createTempFile(directory, speakerId.toString(), ".part");
		try {
			Files.write(temp, content);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
		finally {
			Files.deleteIfExists(temp);
		}
	}

	private static byte[] readAtMost(InputStream in, long limit) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int read;
		while ((read = in.read(chunk)) != -1) {
			if (buffer.size() + read > limit) {
				throw new IOException("image exceeds the configured maximum of " + limit + " bytes");
			}
			buffer.write(chunk, 0, read);
		}
		if (buffer.size() == 0) {
			throw new IOException("empty response body");
		}
		return buffer.toByteArray();
	}

	/**
	 * Determines the file extension from the response content type, falling back to the
	 * URL path. Content type wins: it is what the server actually sent, whereas the path
	 * is attacker-chosen.
	 */
	private static String resolveExtension(HttpResponse<?> response, URI uri) throws IOException {
		String contentType = response.headers()
			.firstValue("content-type")
			.map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
			.orElse("");
		String fromContentType = EXTENSION_BY_CONTENT_TYPE.get(contentType);
		if (fromContentType != null) {
			return fromContentType;
		}
		if (!contentType.isEmpty()) {
			throw new IOException("unsupported content type: " + contentType);
		}
		return extensionFromPath(uri).orElse("jpg");
	}

	private static Optional<String> extensionFromPath(URI uri) {
		String path = uri.getPath();
		if (path == null) {
			return Optional.empty();
		}
		int dot = path.lastIndexOf('.');
		if (dot < 0 || dot == path.length() - 1) {
			return Optional.empty();
		}
		String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
		return EXTENSIONS.contains(extension) ? Optional.of(extension) : Optional.empty();
	}

	private static boolean isRedirect(int statusCode) {
		return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
	}

	private void validateDestination(URI uri) throws IOException {
		String scheme = uri.getScheme();
		if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
			throw new IOException("disallowed URL scheme: " + scheme);
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new IOException("URL has no host");
		}
		if (config.isAllowPrivateNetworkPhotoUrls()) {
			return;
		}
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		}
		catch (UnknownHostException ex) {
			throw new IOException("cannot resolve host: " + host, ex);
		}
		for (InetAddress address : addresses) {
			if (isPrivate(address)) {
				throw new IOException("refusing to fetch from non-public address " + address.getHostAddress());
			}
		}
	}

	private static boolean isPrivate(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		byte[] bytes = address.getAddress();
		// IPv6 unique local addresses (fc00::/7) — not covered by isSiteLocalAddress,
		// which only checks the deprecated fec0::/10 range.
		return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
	}

	private Path photoDirectory() {
		return Path.of(config.getPhotoDir());
	}

	private static MediaType contentType(String extension) {
		return CONTENT_TYPES.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM);
	}

}
