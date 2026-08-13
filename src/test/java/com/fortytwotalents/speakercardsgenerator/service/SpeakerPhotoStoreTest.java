package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.StorageConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpeakerPhotoStore}, with particular attention to the download
 * hardening: profile picture URLs arrive from third-party CFP systems and are untrusted.
 */
class SpeakerPhotoStoreTest {

	private static final UUID SPEAKER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	/** Matches the fixture committed under {@code src/test/resources}. */
	private static final UUID BUNDLED_SPEAKER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@TempDir
	Path photoDir;

	private HttpServer server;

	private StorageConfig config;

	private SpeakerPhotoStore store;

	@BeforeEach
	void setUp() throws IOException {
		this.config = new StorageConfig();
		this.config.setPhotoDir(this.photoDir.toString());
		// The stub server is on loopback, which the SSRF guard blocks by design.
		this.config.setAllowPrivateNetworkPhotoUrls(true);
		this.store = new SpeakerPhotoStore(this.config);
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.start();
	}

	@AfterEach
	void tearDown() {
		this.server.stop(0);
	}

	@Test
	void downloadStoresImageAndFindReturnsItWithMatchingContentType() {
		serve("/jane.png", 200, "image/png", pngBytes());

		boolean stored = store().download(SPEAKER_ID, url("/jane.png"));

		assertThat(stored).isTrue();
		assertThat(this.photoDir.resolve(SPEAKER_ID + ".png")).exists();
		assertThat(store().find(SPEAKER_ID)).hasValueSatisfying(photo -> {
			assertThat(photo.contentType()).isEqualTo(MediaType.IMAGE_PNG);
			assertThat(photo.content()).isNotEmpty();
		});
	}

	@Test
	void extensionComesFromContentTypeNotFromTheAttackerControlledPath() {
		serve("/spoofed.jpg", 200, "image/png", pngBytes());

		assertThat(store().download(SPEAKER_ID, url("/spoofed.jpg"))).isTrue();

		assertThat(this.photoDir.resolve(SPEAKER_ID + ".png")).exists();
		assertThat(this.photoDir.resolve(SPEAKER_ID + ".jpg")).doesNotExist();
	}

	@Test
	void downloadFollowsRedirects() {
		serve("/redirect", 302, "text/plain", new byte[0], "/final.png");
		serve("/final.png", 200, "image/png", pngBytes());

		assertThat(store().download(SPEAKER_ID, url("/redirect"))).isTrue();
		assertThat(this.photoDir.resolve(SPEAKER_ID + ".png")).exists();
	}

	@Test
	void downloadRefusesLoopbackAddressWhenPrivateNetworksAreDisallowed() {
		this.config.setAllowPrivateNetworkPhotoUrls(false);
		serve("/metadata", 200, "image/png", pngBytes());

		assertThat(store().download(SPEAKER_ID, url("/metadata"))).isFalse();
		assertThat(this.photoDir).isEmptyDirectory();
	}

	@Test
	void downloadRefusesLinkLocalMetadataEndpoint() {
		this.config.setAllowPrivateNetworkPhotoUrls(false);

		boolean stored = store().download(SPEAKER_ID,
				"http://169.254.169.254/latest/meta-data/iam/security-credentials/");

		assertThat(stored).isFalse();
		assertThat(this.photoDir).isEmptyDirectory();
	}

	@Test
	void downloadRefusesNonHttpSchemes() {
		assertThat(store().download(SPEAKER_ID, "file:///etc/passwd")).isFalse();
		assertThat(store().download(SPEAKER_ID, "ftp://example.com/a.png")).isFalse();
		assertThat(this.photoDir).isEmptyDirectory();
	}

	@Test
	void downloadAbortsWhenTheImageExceedsTheConfiguredLimit() {
		this.config.setMaxPhotoSize(DataSize.ofBytes(64));
		serve("/huge.png", 200, "image/png", new byte[8192]);

		assertThat(store().download(SPEAKER_ID, url("/huge.png"))).isFalse();
		assertThat(this.photoDir).isEmptyDirectory();
	}

	@Test
	void downloadRejectsNonImageContentType() {
		serve("/creds.png", 200, "application/json", "{\"secret\":\"...\"}".getBytes());

		assertThat(store().download(SPEAKER_ID, url("/creds.png"))).isFalse();
		assertThat(this.photoDir).isEmptyDirectory();
	}

	@Test
	void downloadRejectsErrorResponses() {
		serve("/missing.png", 404, "text/plain", "nope".getBytes());

		assertThat(store().download(SPEAKER_ID, url("/missing.png"))).isFalse();
		assertThat(this.photoDir).isEmptyDirectory();
	}

	@Test
	void downloadIsSkippedWhenAPhotoIsAlreadyStored() throws IOException {
		Files.write(this.photoDir.resolve(SPEAKER_ID + ".jpg"), new byte[] { 1, 2, 3 });
		serve("/jane.png", 200, "image/png", pngBytes());

		assertThat(store().download(SPEAKER_ID, url("/jane.png"))).isFalse();
		assertThat(this.photoDir.resolve(SPEAKER_ID + ".png")).doesNotExist();
	}

	@Test
	void downloadIgnoresMissingInput() {
		assertThat(store().download(null, "http://example.com/a.png")).isFalse();
		assertThat(store().download(SPEAKER_ID, null)).isFalse();
		assertThat(store().download(SPEAKER_ID, "  ")).isFalse();
	}

	@Test
	void findFallsBackToBundledClasspathPhotos() {
		assertThat(store().find(BUNDLED_SPEAKER_ID)).isPresent();
		assertThat(store().exists(BUNDLED_SPEAKER_ID)).isTrue();
	}

	@Test
	void findReturnsEmptyForUnknownSpeaker() {
		assertThat(store().find(UUID.randomUUID())).isEmpty();
		assertThat(store().find(null)).isEmpty();
		assertThat(store().exists(null)).isFalse();
	}

	private SpeakerPhotoStore store() {
		return this.store;
	}

	private String url(String path) {
		return "http://127.0.0.1:" + this.server.getAddress().getPort() + path;
	}

	private void serve(String path, int status, String contentType, byte[] body) {
		serve(path, status, contentType, body, null);
	}

	private void serve(String path, int status, String contentType, byte[] body, String location) {
		this.server.createContext(path, new StubHandler(status, contentType, body, location));
	}

	private record StubHandler(int status, String contentType, byte[] body, String location) implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			exchange.getResponseHeaders().add("Content-Type", this.contentType);
			// Without this the JDK client keeps the socket pooled and HttpServer.stop()
			// blocks for its full drain timeout at the end of every test.
			exchange.getResponseHeaders().add("Connection", "close");
			if (this.location != null) {
				exchange.getResponseHeaders().add("Location", this.location);
			}
			exchange.sendResponseHeaders(this.status, this.body.length == 0 ? -1 : this.body.length);
			if (this.body.length > 0) {
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(this.body);
				}
			}
			exchange.close();
		}
	}

	private static byte[] pngBytes() {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "PNG", out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
