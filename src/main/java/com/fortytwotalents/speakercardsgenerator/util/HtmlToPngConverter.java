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
 * such as {@code /static/images/background.png} are resolved via the running application
 * server at {@code http://localhost:{server.port}}. The caller must therefore ensure the
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
	 * The HTML is rendered at screen resolution (96 DPI) and the first page of the
	 * resulting PDF is then converted to PNG.
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
			// 72 DPI = 1 pt → 1 px, which maps the PDF page pixels 1:1 to image pixels
			BufferedImage image = renderer.renderImageWithDPI(0, 72, ImageType.ARGB);
			ImageIO.write(image, "PNG", pngOut);
			return pngOut.toByteArray();
		}
	}

}
