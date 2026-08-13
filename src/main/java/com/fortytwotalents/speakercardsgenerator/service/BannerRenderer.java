package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.util.TemplateUtils;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders the banner templates to HTML and to PNG.
 *
 * <p>
 * This is the one place that knows how a banner is produced. Bulk generation used to
 * re-render banners by issuing HTTP requests back to the application's own PNG endpoints,
 * which made it depend on the server being reachable at a guessed
 * {@code http://host:port} — wrong behind a context path, a random port, TLS or a proxy,
 * and impossible to unit test. Both the controller and {@link BannerGenerationService}
 * now call straight into this service instead.
 */
@Service
public class BannerRenderer {

	/** The kinds of banner the application can produce. */
	public enum BannerType {

		/** 16:9 speaker card. */
		SPEAKER("banner/speakerBanner", "speaker"),

		/** 16:9 talk card. */
		TALK("banner/talkBanner", "talks"),

		/** Square social-media card. */
		SOCIAL("banner/speakerSocial", "social");

		private final String view;

		private final String directory;

		BannerType(String view, String directory) {
			this.view = view;
			this.directory = directory;
		}

		/** Thymeleaf view name for this banner. */
		public String view() {
			return this.view;
		}

		/** Directory this banner type is written to inside output folders and ZIPs. */
		public String directory() {
			return this.directory;
		}

	}

	private final SpringTemplateEngine templateEngine;

	private final EventConfig eventConfig;

	private final TemplateUtils templateUtils;

	private final HtmlToPngConverter converter;

	public BannerRenderer(SpringTemplateEngine templateEngine, EventConfig eventConfig, TemplateUtils templateUtils,
			HtmlToPngConverter converter) {
		this.templateEngine = templateEngine;
		this.eventConfig = eventConfig;
		this.templateUtils = templateUtils;
		this.converter = converter;
	}

	/**
	 * Renders a banner to HTML.
	 * @param type which banner to render
	 * @param speaker the speaker; {@code null} for {@link BannerType#TALK}
	 * @param talk the talk; may be {@code null} when a speaker has no session
	 * @return the rendered HTML
	 */
	public String renderHtml(BannerType type, Speaker speaker, Talk talk) {
		Context context = new Context();
		context.setVariable("speaker", speaker);
		context.setVariable("talk", talk);
		context.setVariable("event", this.eventConfig);
		context.setVariable("utils", this.templateUtils);
		return this.templateEngine.process(type.view, context);
	}

	/**
	 * Renders a banner to PNG.
	 * @param type which banner to render
	 * @param speaker the speaker; {@code null} for {@link BannerType#TALK}
	 * @param talk the talk; may be {@code null} when a speaker has no session
	 * @return PNG bytes
	 */
	public byte[] renderPng(BannerType type, Speaker speaker, Talk talk) {
		return this.converter.convertToPng(renderHtml(type, speaker, talk));
	}

	/**
	 * Renders a speaker-centric banner, using the speaker's first talk if there is one.
	 */
	public byte[] renderSpeakerPng(BannerType type, Speaker speaker) {
		return renderPng(type, speaker, firstTalk(speaker));
	}

	/** Renders a speaker-centric banner as HTML. */
	public String renderSpeakerHtml(BannerType type, Speaker speaker) {
		return renderHtml(type, speaker, firstTalk(speaker));
	}

	/**
	 * The talk shown alongside a speaker on their card. Speakers with several accepted
	 * sessions get their first one.
	 */
	public static Talk firstTalk(Speaker speaker) {
		if (speaker == null || speaker.getTalks() == null || speaker.getTalks().isEmpty()) {
			return null;
		}
		return speaker.getTalks().get(0);
	}

	/**
	 * File name used for a speaker banner inside ZIP archives and output directories,
	 * e.g. {@code speaker/Doe_Jane.png}.
	 */
	public static String fileName(BannerType type, Speaker speaker) {
		return type.directory() + "/" + sanitise(speaker.getLastName()) + "_" + sanitise(speaker.getFirstName())
				+ ".png";
	}

	/** File name used for a talk banner, e.g. {@code talks/1234.png}. */
	public static String fileName(Talk talk) {
		return BannerType.TALK.directory() + "/" + talk.getId() + ".png";
	}

	/**
	 * Strips characters that are unsafe in a file name or ZIP entry — including path
	 * separators, so a speaker name can never escape its directory.
	 */
	private static String sanitise(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		String cleaned = value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
		return cleaned.replace("..", "_").toLowerCase(Locale.ROOT).isBlank() ? "unknown" : cleaned.replace("..", "_");
	}

}
