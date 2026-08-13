package com.fortytwotalents.speakercardsgenerator.service;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.extend.FSUriResolver;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.slf4j.Slf4jLogger;
import com.openhtmltopdf.util.XRLog;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Converts banner HTML to a PNG image: OpenHTMLtoPDF lays the page out and renders it to
 * a PDF, then PDFBox rasterises that PDF.
 *
 * <p>
 * Every resource the page references — web fonts, the event logo, speaker photos — is
 * resolved straight from the classpath and the {@link SpeakerPhotoStore}. Nothing is
 * fetched over the network, so rendering works with no server listening, behind any
 * context path, on a random port, and in a plain unit test.
 */
@Component
public class HtmlToPngConverter {

	private static final Logger log = LoggerFactory.getLogger(HtmlToPngConverter.class);

	/**
	 * Pseudo-scheme for resources served by this application. Using a scheme of our own
	 * means the renderer can never fall back to the built-in HTTP stream factory.
	 */
	private static final String SCHEME = "app:";

	private static final String STATIC_ROOT = "/static";

	private static final String SPEAKER_PHOTO_PREFIX = "/speaker-photo/";

	private static final String PLACEHOLDER_PHOTO = "/images/duke_cool.png";

	/**
	 * The templates declare a 1280×720 CSS-pixel page, which at 96 CSS DPI is a 960×540
	 * pt PDF page. Rasterising that at 144 DPI scales by 144/96 = 1.5, giving the
	 * documented 1920×1080 PNG: crisp circles and sharp text for social media.
	 */
	private static final int RENDER_DPI = 144;

	static {
		// Route the renderer's own logging into SLF4J once, rather than silencing it on
		// every conversion. Levels are now controlled from application.properties like
		// everything else.
		XRLog.setLoggerImpl(new Slf4jLogger());
	}

	private final SpeakerPhotoStore photoStore;

	public HtmlToPngConverter(SpeakerPhotoStore photoStore) {
		this.photoStore = photoStore;
	}

	/**
	 * Converts banner HTML to PNG bytes.
	 * @param html HTML content to render; must be parseable by the HTML5 parser
	 * @return PNG bytes for the first rendered page
	 * @throws BannerRenderingException if rendering or image conversion fails
	 */
	public byte[] convertToPng(String html) {
		try {
			return rasterise(renderToPdf(html));
		}
		catch (Exception ex) {
			throw new BannerRenderingException("Failed to convert HTML to PNG", ex);
		}
	}

	private byte[] renderToPdf(String html) throws IOException {
		try (ByteArrayOutputStream pdfOut = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.withHtmlContent(html, SCHEME + "/");
			builder.useUriResolver(new AppUriResolver());
			builder.useProtocolsStreamImplementation(new AppStreamFactory(), "app");
			// Page size comes from the CSS @page rule in the template itself.
			builder.toStream(pdfOut);
			builder.run();
			return pdfOut.toByteArray();
		}
	}

	private byte[] rasterise(byte[] pdfBytes) throws IOException {
		try (PDDocument pdfDoc = Loader.loadPDF(pdfBytes); ByteArrayOutputStream pngOut = new ByteArrayOutputStream()) {
			BufferedImage image = new PDFRenderer(pdfDoc).renderImageWithDPI(0, RENDER_DPI, ImageType.ARGB);
			ImageIO.write(image, "PNG", pngOut);
			return pngOut.toByteArray();
		}
	}

	/** Rewrites every reference in the page to the {@code app:} scheme. */
	private static final class AppUriResolver implements FSUriResolver {

		@Override
		public String resolveURI(String baseUri, String uri) {
			if (uri == null || uri.isBlank()) {
				return null;
			}
			String trimmed = uri.trim();
			if (trimmed.startsWith("data:") || trimmed.startsWith(SCHEME)) {
				return trimmed;
			}
			if (trimmed.contains("://")) {
				// Banner templates only reference local assets. Refusing remote URLs
				// keeps rendering deterministic and offline.
				log.warn("Ignoring remote resource referenced by a banner template: {}", trimmed);
				return null;
			}
			return SCHEME + (trimmed.startsWith("/") ? trimmed : "/" + trimmed);
		}

	}

	/** Serves {@code app:} URLs from the classpath and the speaker photo store. */
	private final class AppStreamFactory implements FSStreamFactory {

		@Override
		public FSStream getUrl(String url) {
			String path = url.startsWith(SCHEME) ? url.substring(SCHEME.length()) : url;
			int query = path.indexOf('?');
			if (query >= 0) {
				path = path.substring(0, query);
			}
			byte[] content = path.startsWith(SPEAKER_PHOTO_PREFIX) ? speakerPhoto(path) : staticResource(path);
			if (content == null) {
				log.warn("Banner template referenced a resource that could not be resolved: {}", url);
				content = new byte[0];
			}
			return new ByteArrayStream(content);
		}

		private byte[] speakerPhoto(String path) {
			String id = path.substring(SPEAKER_PHOTO_PREFIX.length());
			return speakerId(id).flatMap(HtmlToPngConverter.this.photoStore::find)
				.map(SpeakerPhotoStore.Photo::content)
				.orElseGet(() -> staticResource(PLACEHOLDER_PHOTO));
		}

		private Optional<UUID> speakerId(String value) {
			try {
				return Optional.of(UUID.fromString(value));
			}
			catch (IllegalArgumentException ex) {
				return Optional.empty();
			}
		}

		private byte[] staticResource(String path) {
			// Normalise first so a template — or a crafted speaker id — cannot walk out
			// of /static via `..` segments.
			String normalised = Path.of(path).normalize().toString().replace('\\', '/');
			if (!normalised.startsWith("/")) {
				return null;
			}
			try (InputStream in = HtmlToPngConverter.class.getResourceAsStream(STATIC_ROOT + normalised)) {
				return in != null ? in.readAllBytes() : null;
			}
			catch (IOException ex) {
				log.warn("Could not read bundled resource {}", normalised, ex);
				return null;
			}
		}

	}

	private record ByteArrayStream(byte[] content) implements FSStream {

		@Override
		public InputStream getStream() {
			return new ByteArrayInputStream(this.content);
		}

		@Override
		public Reader getReader() {
			return new InputStreamReader(getStream(), StandardCharsets.UTF_8);
		}

	}

	/** Thrown when a banner cannot be rendered. */
	public static class BannerRenderingException extends RuntimeException {

		BannerRenderingException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
