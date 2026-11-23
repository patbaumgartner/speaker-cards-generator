package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.util.TemplateUtils;
import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
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
		SPEAKER("banner/speakerBanner", "speaker", "speaker"),

		/** 16:9 talk card. */
		TALK("banner/talkBanner", "sessions", "session"),

		/** Square social-media card. */
		SOCIAL("banner/speakerSocial", "social", "social");

		private final String view;

		private final String directory;

		private final String suffix;

		BannerType(String view, String directory, String suffix) {
			this.view = view;
			this.directory = directory;
			this.suffix = suffix;
		}

		/** Thymeleaf view name for this banner. */
		public String view() {
			return this.view;
		}

		/** Directory this banner type is written to inside output folders and ZIPs. */
		public String directory() {
			return this.directory;
		}

		/** Banner kind appended to download file names. */
		public String suffix() {
			return this.suffix;
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
	 * Default session for legacy speaker URLs without an explicit session selection.
	 */
	public static Talk firstTalk(Speaker speaker) {
		if (speaker == null || speaker.getTalks() == null || speaker.getTalks().isEmpty()) {
			return null;
		}
		return speaker.getTalks().get(0);
	}

	/** Speakers without sessions still receive a general speaker/social banner. */
	static List<Talk> bannerTalks(Speaker speaker) {
		return speaker.getTalks().isEmpty() ? Collections.singletonList(null)
				: speaker.getTalks().stream().sorted().toList();
	}

	/** Relative path shared by ZIP archives and disk exports. */
	public String fileName(BannerType type, Speaker speaker, Talk talk) {
		return type.directory() + "/" + downloadFileName(type, speaker, talk);
	}

	/** Download names include stable IDs so duplicate names and titles cannot collide. */
	public String downloadFileName(BannerType type, Speaker speaker, Talk talk) {
		String name = slug(this.eventConfig.getShortName(), 24);
		if (talk != null) {
			String category = talk.getSessionTypeLabel();
			name += "-" + slug(category == null || category.isBlank() ? "session" : category, 24) + "-" + talk.getId()
					+ "-" + slug(talk.getTitle(), 80);
		}
		if (speaker != null) {
			name += "-" + slug(speaker.displayName(), 48) + "-" + speaker.getId();
		}
		return name + "-" + type.suffix() + ".png";
	}

	private static String slug(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		String cleaned = Normalizer.normalize(value, Normalizer.Form.NFKD)
			.replaceAll("\\p{M}+", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("^-|-$", "");
		if (cleaned.isEmpty()) {
			return "unknown";
		}
		return cleaned.substring(0, Math.min(cleaned.length(), maxLength)).replaceAll("-$", "");
	}

}
