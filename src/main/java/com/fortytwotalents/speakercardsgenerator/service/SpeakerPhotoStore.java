package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.config.StorageConfig;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for speaker profile pictures: downloads them during import and
 * reads them back for the web UI and the banner renderer.
 *
 * <p>
 * Photos are looked up in two places:
 * <ol>
 * <li>the configured runtime directory ({@code app.storage.photo-dir}) — where imports
 * write;</li>
 * <li>the classpath under {@code /static/images/speaker/} — so photos bundled into the
 * image or checked in by hand keep working.</li>
 * </ol>
 * The highest-resolution candidate wins; ties go to the byte-rich file, then to the
 * runtime copy. Photos are normalised on display and rendering: cropped to a uniform
 * square and given a light wash of the active event's accent colour, so mixed source
 * photos read as one set without needing a re-import.
 *
 * <p>
 * Rendering and display use {@link #findOrPlaceholder(UUID)}, which falls back to a
 * locally generated, branded avatar. A speaker therefore never renders with an empty
 * photo area or a mascot placeholder.
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

	private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/gif",
			"image/webp");

	/** Content types that say "binary blob" without naming the actual image format. */
	private static final Set<String> GENERIC_CONTENT_TYPES = Set.of("binary/octet-stream", "application/octet-stream",
			"application/binary");

	private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n' };

	private static final byte[] GIF_87_MAGIC = "GIF87a".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] GIF_89_MAGIC = "GIF89a".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] RIFF_MAGIC = "RIFF".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] WEBP_MAGIC = "WEBP".getBytes(StandardCharsets.US_ASCII);

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

	private static final String CLASSPATH_PREFIX = "/static/images/speaker/";

	private static final int MAX_REDIRECTS = 3;

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private static final byte[] DEFAULT_PHOTO = createDefaultPhoto();

	/** Uniform size for displayed portraits, independent of the source resolution. */
	private static final int PORTRAIT_SIZE = 600;

	/** How much of the event accent colour is mixed into the grayscale portrait. */
	private static final float TINT_STRENGTH = 0.25f;

	/** Stylised portraits, keyed by the source bytes and the active theme colours. */
	private final Map<String, Photo> stylizedPhotos = new ConcurrentHashMap<>();

	private final StorageConfig config;

	private final EventConfig event;

	private final HttpClient httpClient;

	public SpeakerPhotoStore(StorageConfig config, EventConfig event) {
		this.config = config;
		this.event = event;
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
		List<Candidate> candidates = new ArrayList<>();
		for (String extension : EXTENSIONS) {
			Path file = photoDirectory().resolve(speakerId + "." + extension);
			if (Files.isRegularFile(file)) {
				try {
					candidates.add(toCandidate(Files.readAllBytes(file), extension));
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
					candidates.add(toCandidate(in.readAllBytes(), extension));
				}
			}
			catch (IOException ex) {
				log.warn("Could not read bundled profile picture {}", resource, ex);
			}
		}
		return candidates.stream()
			.max(Comparator.comparingLong(Candidate::pixels)
				.thenComparingInt(candidate -> candidate.photo().content().length))
			.map(Candidate::photo);
	}

	private static Candidate toCandidate(byte[] content, String extension) {
		return new Candidate(new Photo(content, contentType(extension)), pixelCountOrZero(content));
	}

	private static long pixelCountOrZero(byte[] content) {
		try {
			return pixelCount(content);
		}
		catch (IOException ex) {
			log.warn("Could not determine image dimensions", ex);
			return 0;
		}
	}

	private record Candidate(Photo photo, long pixels) {
	}

	/**
	 * Finds the speaker's photo, or returns the branded default avatar. Use this for
	 * display and rendering; {@link #find(UUID)} stays empty-aware so imports can still
	 * download a real photo later.
	 * @param speakerId speaker UUID
	 * @return a real photo when available, otherwise the default avatar
	 */
	public Photo findOrPlaceholder(UUID speakerId) {
		return find(speakerId)
			.map(photo -> this.stylizedPhotos.computeIfAbsent(fingerprint(photo.content(), this.event),
					key -> stylizePhoto(photo)))
			.orElseGet(SpeakerPhotoStore::defaultPhoto);
	}

	private Photo stylizePhoto(Photo photo) {
		try {
			return new Photo(stylize(photo.content(), this.event), MediaType.IMAGE_PNG);
		}
		catch (IOException ex) {
			log.warn("Could not stylise portrait; keeping the original", ex);
			return photo;
		}
	}

	private static String fingerprint(byte[] content, EventConfig event) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(content);
			digest.update(String.valueOf(event.getColorPrimary()).getBytes(StandardCharsets.UTF_8));
			digest.update(String.valueOf(event.getColorSecondary()).getBytes(StandardCharsets.UTF_8));
			digest.update(String.valueOf(event.getColorAccent()).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required to cache portrait styles", ex);
		}
	}

	/** Returns the locally generated avatar used when a speaker has no photo. */
	public static Photo defaultPhoto() {
		return new Photo(DEFAULT_PHOTO, MediaType.IMAGE_PNG);
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
	 * Refreshes a profile picture with the bytes supplied by the CFP. Never throws: a
	 * failed photo download must not abort an otherwise successful speaker import.
	 * @param speakerId speaker UUID, used as the file name
	 * @param sourceUrls available portrait URLs, compared by resolution then file size
	 * @return whether a portrait was stored
	 */
	public boolean download(UUID speakerId, String... sourceUrls) {
		if (speakerId == null || sourceUrls == null) {
			return false;
		}
		Portrait best = null;
		for (String url : Arrays.stream(sourceUrls)
			.filter(u -> u != null && !u.isBlank())
			.map(String::trim)
			.distinct()
			.toList()) {
			try {
				Portrait candidate = fetch(url);
				if (best == null || isBetter(candidate, best)) {
					best = candidate;
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
			catch (IOException | IllegalArgumentException ex) {
				log.warn("Could not fetch portrait for speaker {}: {}", speakerId, ex.getMessage());
			}
		}
		if (best == null) {
			return false;
		}
		try {
			write(speakerId, best.extension(), best.content());
			return true;
		}
		catch (IOException ex) {
			log.warn("Could not store portrait for speaker {}", speakerId, ex);
			return false;
		}
	}

	/**
	 * Crops a portrait to a square, scales it to {@link #PORTRAIT_SIZE}, and adds a light
	 * wash of the event's accent colour. No contrast stretching or smoothing: the source
	 * tones stay as they are, just squared, resized and lightly tinted.
	 */
	static byte[] stylize(byte[] content, EventConfig event) throws IOException {
		BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
		if (source == null) {
			throw new IOException("unsupported image format");
		}
		return png(tintImage(squareCrop(source), accentColor(event)));
	}

	private static BufferedImage squareCrop(BufferedImage source) {
		int side = Math.min(source.getWidth(), source.getHeight());
		int x = (source.getWidth() - side) / 2;
		// Bias the crop above centre so faces survive wide or full-body source photos.
		int y = (int) ((source.getHeight() - side) * 0.35);
		BufferedImage square = new BufferedImage(PORTRAIT_SIZE, PORTRAIT_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = square.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(source, 0, 0, PORTRAIT_SIZE, PORTRAIT_SIZE, x, y, x + side, y + side, null);
		}
		finally {
			graphics.dispose();
		}
		return square;
	}

	private static int accentColor(EventConfig event) {
		String hex = event.getColorAccent();
		if (hex == null || hex.length() != 7) {
			return 0xFF0099;
		}
		try {
			return Color.decode(hex).getRGB() & 0xFFFFFF;
		}
		catch (IllegalArgumentException ex) {
			return 0xFF0099;
		}
	}

	private static BufferedImage tintImage(BufferedImage image, int accent) {
		BufferedImage result = new BufferedImage(PORTRAIT_SIZE, PORTRAIT_SIZE, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < PORTRAIT_SIZE; y++) {
			for (int x = 0; x < PORTRAIT_SIZE; x++) {
				int rgb = image.getRGB(x, y);
				int gray = (299 * ((rgb >> 16) & 0xFF) + 587 * ((rgb >> 8) & 0xFF) + 114 * (rgb & 0xFF)) / 1000;
				int red = mix(gray, accent >> 16);
				int green = mix(gray, accent >> 8);
				int blue = mix(gray, accent);
				result.setRGB(x, y, (red << 16) | (green << 8) | blue);
			}
		}
		return result;
	}

	private static int mix(int gray, int accentChannel) {
		return Math.round(gray + ((accentChannel & 0xFF) - gray) * TINT_STRENGTH);
	}

	private static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", out)) {
			throw new IOException("no PNG writer is available");
		}
		return out.toByteArray();
	}

	private record Portrait(String extension, byte[] content, long pixels) {
	}

	private static boolean isBetter(Portrait candidate, Portrait current) {
		int byPixels = Long.compare(candidate.pixels(), current.pixels());
		return byPixels > 0 || (byPixels == 0 && candidate.content().length > current.content().length);
	}

	private static long pixelCount(byte[] content) throws IOException {
		try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return 0; // Preserve supported formats without an installed ImageIO
							// reader.
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				return (long) reader.getWidth(0) * reader.getHeight(0);
			}
			finally {
				reader.dispose();
			}
		}
	}

	/** Removes a previously imported portrait when the CFP clears its photo URL. */
	public synchronized void removeImportedPhoto(UUID speakerId) throws IOException {
		for (String extension : EXTENSIONS) {
			Files.deleteIfExists(photoDirectory().resolve(speakerId + "." + extension));
		}
	}

	private Portrait fetch(String sourceUrl) throws IOException, InterruptedException {
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
				byte[] content = readAtMost(body, config.getMaxPhotoSize().toBytes());
				String extension = resolveExtension(response, content);
				return new Portrait(extension, content, pixelCount(content));
			}
		}
		throw new IOException("too many redirects");
	}

	private synchronized void write(UUID speakerId, String extension, byte[] content) throws IOException {
		Path directory = photoDirectory();
		Files.createDirectories(directory);
		Path target = directory.resolve(speakerId + "." + extension);
		// Write to a temporary file first so a crashed download never leaves a
		// half-written image that later probes would happily serve.
		Path temp = Files.createTempFile(directory, speakerId.toString(), ".part");
		try {
			Files.write(temp, content);
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			for (String other : EXTENSIONS) {
				if (!other.equals(extension)) {
					Files.deleteIfExists(directory.resolve(speakerId + "." + other));
				}
			}
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
	 * Checks the response type and identifies the stored format from its signature.
	 */
	private static String resolveExtension(HttpResponse<?> response, byte[] content) throws IOException {
		String contentType = response.headers()
			.firstValue("content-type")
			.map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
			.orElse("");
		if (IMAGE_CONTENT_TYPES.contains(contentType) || contentType.isEmpty()
				|| GENERIC_CONTENT_TYPES.contains(contentType)) {
			return extensionFromContent(content).orElseThrow(() -> new IOException("unrecognised image bytes"));
		}
		throw new IOException("unsupported content type: " + contentType);
	}

	private static Optional<String> extensionFromContent(byte[] content) {
		if (startsWith(content, JPEG_MAGIC)) {
			return Optional.of("jpg");
		}
		if (startsWith(content, PNG_MAGIC)) {
			return Optional.of("png");
		}
		if (startsWith(content, GIF_87_MAGIC) || startsWith(content, GIF_89_MAGIC)) {
			return Optional.of("gif");
		}
		if (content.length >= 12 && startsWith(content, RIFF_MAGIC) && startsWith(content, 8, WEBP_MAGIC)) {
			return Optional.of("webp");
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		return startsWith(content, 0, prefix);
	}

	private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
		if (content.length - offset < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (content[offset + i] != prefix[i]) {
				return false;
			}
		}
		return true;
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

	private static byte[] createDefaultPhoto() {
		int size = 512;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(0x1E, 0x22, 0x46));
			graphics.fillRect(0, 0, size, size);
			graphics.setColor(new Color(0x40, 0xB4, 0xE5));
			graphics.fillOval(0, 0, size, size);
			graphics.setColor(new Color(0x1E, 0x22, 0x46));
			graphics.fillOval(184, 120, 144, 144);
			graphics.fill(new Ellipse2D.Double(112, 290, 288, 300));
		}
		finally {
			graphics.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(image, "png", out)) {
				throw new IllegalStateException("No PNG image writer is available");
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not create the default speaker photo", ex);
		}
		return out.toByteArray();
	}

	private Path photoDirectory() {
		return Path.of(config.getPhotoDir());
	}

	private static MediaType contentType(String extension) {
		return CONTENT_TYPES.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM);
	}

}
