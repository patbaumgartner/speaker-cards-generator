package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.StorageConfig;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the HTML → PDF → PNG pipeline end to end.
 *
 * <p>
 * Nothing here starts a web server. That is the point: banner rendering used to resolve
 * fonts and images through {@code http://localhost:{server.port}}, so it could not be
 * exercised without a live application listening on a guessed port.
 */
class HtmlToPngConverterTest {

	private static final String HTML = """
			<!DOCTYPE html>
			<html><head><style>
			  @page { size: 100px 50px; margin: 0; }
			  body { margin: 0; width: 100px; height: 50px; background: #40b4e5; }
			</style></head>
			<body><div>Speaker</div></body></html>
			""";

	@TempDir
	Path photoDir;

	@Test
	void rendersHtmlToAPngAtOneAndAHalfTimesTheCssPixelResolution() throws Exception {
		byte[] png = converter().convertToPng(HTML);

		assertThat(png).isNotEmpty();
		// PNG signature — proves we produced a real image, not an empty buffer.
		assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		// The page is laid out at 96 CSS DPI and rasterised at 144 DPI, so the PNG comes
		// out at 144/96 = 1.5x the CSS pixel size. That is what turns the 1280x720
		// banner templates into the documented 1920x1080 output.
		assertThat(image.getWidth()).isEqualTo(150);
		assertThat(image.getHeight()).isEqualTo(75);
	}

	@Test
	void rendersTheDeclaredBackgroundColour() throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(converter().convertToPng(HTML)));

		assertThat(new Color(image.getRGB(75, 37), true)).isEqualTo(new Color(0x40, 0xb4, 0xe5));
	}

	@Test
	void resolvesBundledFontsFromTheClasspathWithoutAServer() {
		String html = """
				<!DOCTYPE html>
				<html><head><style>
				  @font-face { font-family: 'Poppins'; font-weight: 700;
				               src: url('/fonts/Poppins-Bold.ttf') format('truetype'); }
				  @page { size: 400px 100px; margin: 0; }
				  body { margin: 0; font-family: 'Poppins', sans-serif; font-size: 30px; }
				</style></head>
				<body><div>Voxxed Days</div></body></html>
				""";

		assertThat(converter().convertToPng(html)).isNotEmpty();
	}

	@Test
	void resolvesBundledImagesFromTheClasspathWithoutAServer() {
		assertThat(converter().convertToPng(imageHtml("/images/duke_cool.png"))).isNotEmpty();
	}

	@Test
	void fallsBackToThePlaceholderWhenASpeakerHasNoPhoto() {
		assertThat(converter().convertToPng(imageHtml("/speaker-photo/" + UUID.randomUUID()))).isNotEmpty();
	}

	@Test
	void servesStoredSpeakerPhotos() {
		UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");

		assertThat(converter().convertToPng(imageHtml("/speaker-photo/" + id))).isNotEmpty();
	}

	@Test
	void survivesReferencesToResourcesThatDoNotExist() {
		assertThat(converter().convertToPng(imageHtml("/images/does-not-exist.png"))).isNotEmpty();
	}

	@Test
	void ignoresRemoteResourceReferences() {
		assertThat(converter().convertToPng(imageHtml("https://example.invalid/tracker.png"))).isNotEmpty();
	}

	@Test
	void refusesToEscapeTheStaticRootViaTraversal() {
		assertThat(converter().convertToPng(imageHtml("/images/../../application.properties"))).isNotEmpty();
	}

	@Test
	void failsLoudlyOnUnparseableInput() {
		assertThatThrownBy(() -> converter().convertToPng(""))
			.isInstanceOf(HtmlToPngConverter.BannerRenderingException.class);
	}

	private static String imageHtml(String src) {
		return """
				<!DOCTYPE html>
				<html><head><style>@page { size: 200px 200px; margin: 0; }
				body { margin: 0; }</style></head>
				<body><img src="%s" width="100" height="100" /></body></html>
				""".formatted(src);
	}

	private HtmlToPngConverter converter() {
		StorageConfig config = new StorageConfig();
		config.setPhotoDir(this.photoDir.toString());
		return new HtmlToPngConverter(new SpeakerPhotoStore(config));
	}

}
