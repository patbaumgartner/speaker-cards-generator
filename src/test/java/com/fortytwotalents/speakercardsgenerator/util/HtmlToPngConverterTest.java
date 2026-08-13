package com.fortytwotalents.speakercardsgenerator.util;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the HTML → PDF → PNG pipeline end to end.
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

	@Test
	void rendersHtmlToAPngAtOneAndAHalfTimesTheCssPixelResolution() throws Exception {
		byte[] png = converter().convertToPng(HTML);

		assertThat(png).isNotEmpty();
		// PNG signature — proves we produced an actual image, not an empty buffer.
		assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		// The page is laid out at 96 CSS DPI and rasterised at 144 DPI, so the PNG comes
		// out at 144/96 = 1.5x the CSS pixel size. This is what turns the 1280x720
		// banner templates into the documented 1920x1080 output.
		assertThat(image.getWidth()).isEqualTo(150);
		assertThat(image.getHeight()).isEqualTo(75);
	}

	@Test
	void rendersTheDeclaredBackgroundColour() throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(converter().convertToPng(HTML)));

		assertThat(new java.awt.Color(image.getRGB(100, 50), true)).isEqualTo(new java.awt.Color(0x40, 0xb4, 0xe5));
	}

	@Test
	void failsLoudlyOnUnparseableInput() {
		assertThatThrownBy(() -> converter().convertToPng("")).isInstanceOf(RuntimeException.class);
	}

	private HtmlToPngConverter converter() {
		HtmlToPngConverter converter = new HtmlToPngConverter();
		ReflectionTestUtils.setField(converter, "serverPort", 8080);
		return converter;
	}

}
