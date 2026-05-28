package com.fortytwotalents.speakercardsgenerator.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Converts an HTML string to a PNG image using OpenHTMLtoPDF for HTML→PDF rendering and
 * Apache PDFBox for PDF→PNG conversion.
 *
 * <p>
 * Static resources (fonts, background images) referenced in the HTML with absolute paths
 * such as {@code /images/background.png} are resolved via the running application server
 * at {@code http://localhost:{server.port}}. The caller must therefore ensure the
 * application is fully started before invoking this converter.
 */
@Component
public class HtmlToPngConverter {

	private static final Logger log = LoggerFactory.getLogger(HtmlToPngConverter.class);

	@Value("${server.port:8080}")
	private int serverPort;

	/**
	 * Converts an HTML string to a PNG byte array.
	 *
	 * <p>
	 * The HTML is laid out at 96 CSS DPI (standard browser rendering), producing a PDF
	 * page of 960×540 pt. That page is then rasterised at 144 DPI, yielding a 1920×1080
	 * px PNG — crisp retina-quality output suited for social-media cards.
	 * @param html HTML content to render; must be parseable by the HTML5 parser
	 * @return PNG bytes for the first rendered page
	 * @throws RuntimeException if rendering or image conversion fails
	 */
	public byte[] convertToPng(String html) {
		// Suppress excessive OpenHTMLtoPDF logging
		XRLog.setLoggingEnabled(false);

		try {
			byte[] pdfBytes = renderToPdf(html);
			return pdfToImage(pdfBytes);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to convert HTML to PNG", e);
		}
	}

	private byte[] renderToPdf(String html) throws Exception {
		String baseUri = "http://localhost:" + serverPort;

		try (ByteArrayOutputStream pdfOut = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.withHtmlContent(html, baseUri);
			// Page size is controlled by the CSS @page rule in the template itself.
			// No explicit page size override is required here.
			builder.toStream(pdfOut);
			builder.run();
			return pdfOut.toByteArray();
		}
	}

	private byte[] pdfToImage(byte[] pdfBytes) throws Exception {
		try (PDDocument pdfDoc = PDDocument.load(pdfBytes);
				ByteArrayOutputStream pngOut = new ByteArrayOutputStream()) {

			PDFRenderer renderer = new PDFRenderer(pdfDoc);
			// 144 DPI: the CSS @page is 1280×720 CSS px (at 96 dpi → 960×540 pt PDF).
			// Rendering at 144 DPI doubles the CSS-pixel resolution to 1920×1080,
			// producing crisp edges on circular elements and sharp text for social media.
			BufferedImage image = renderer.renderImageWithDPI(0, 144, ImageType.ARGB);
			ImageIO.write(image, "PNG", pngOut);
			return pngOut.toByteArray();
		}
	}

}
