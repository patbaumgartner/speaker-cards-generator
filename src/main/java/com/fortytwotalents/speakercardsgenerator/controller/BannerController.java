package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
import com.fortytwotalents.speakercardsgenerator.service.BannerGenerationResult;
import com.fortytwotalents.speakercardsgenerator.service.BannerGenerationService;
import com.fortytwotalents.speakercardsgenerator.util.HtmlToPngConverter;
import com.fortytwotalents.speakercardsgenerator.util.TemplateUtils;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Spring MVC controller for banner HTML preview and PNG generation.
 *
 * <p>
 * HTML preview endpoints return Thymeleaf-rendered HTML (useful for development /
 * tweaking templates). PNG endpoints convert the same HTML to a raster image using
 * OpenHTMLtoPDF and return the bytes with {@code Content-Type: image/png}.
 *
 * <h2>Endpoints</h2>
 *
 * <pre>
 *   GET  /speaker-banner/{id}        – HTML preview of a speaker banner
 *   GET  /speaker-banner/{id}.png    – PNG download of a speaker banner
 *   GET  /speaker-social/{id}.png    – PNG download of a social-media banner
 *   GET  /talk-banner/{id}           – HTML preview of a talk banner
 *   GET  /talk-banner/{id}.png       – PNG download of a talk banner
 *   GET  /speaker-photo/{id}         – Speaker profile photo (redirect to default if missing)
 *   GET  /api/banners/generate-all   – Bulk-generate all banners (optional ?outputDir=…)
 *   POST /api/banners/generate-speakers – Bulk-generate banners for selected speaker IDs
 * </pre>
 */
@Controller
public class BannerController {

	private final SpeakerRepository speakerRepository;

	private final TalkRepository talkRepository;

	private final BannerGenerationService bannerService;

	private final HtmlToPngConverter htmlToPngConverter;

	private final SpringTemplateEngine templateEngine;

	private final EventConfig eventConfig;

	private final TemplateUtils templateUtils;

	public BannerController(SpeakerRepository speakerRepository, TalkRepository talkRepository,
			BannerGenerationService bannerService, HtmlToPngConverter htmlToPngConverter,
			SpringTemplateEngine templateEngine, EventConfig eventConfig, TemplateUtils templateUtils) {
		this.speakerRepository = speakerRepository;
		this.talkRepository = talkRepository;
		this.bannerService = bannerService;
		this.htmlToPngConverter = htmlToPngConverter;
		this.templateEngine = templateEngine;
		this.eventConfig = eventConfig;
		this.templateUtils = templateUtils;
	}

	@GetMapping("/speaker-banner/{id}")
	public String speakerBanner(@PathVariable UUID id, Model model) {
		Speaker speaker = requireSpeaker(id);
		Talk talk = firstTalk(speaker);
		model.addAttribute("speaker", speaker);
		model.addAttribute("talk", talk);
		model.addAttribute("event", eventConfig);
		return "banner/speakerBanner";
	}

	@GetMapping("/talk-banner/{id}")
	public String talkBanner(@PathVariable Long id, Model model) {
		Talk talk = requireTalk(id);
		model.addAttribute("talk", talk);
		model.addAttribute("event", eventConfig);
		return "banner/talkBanner";
	}

	@GetMapping(value = "/speaker-banner/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public byte[] speakerBannerPng(@PathVariable UUID id) {
		Speaker speaker = requireSpeaker(id);
		Talk talk = firstTalk(speaker);
		String html = renderTemplate("banner/speakerBanner", speaker, talk);
		return htmlToPngConverter.convertToPng(html);
	}

	@GetMapping(value = "/speaker-social/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public byte[] speakerSocialPng(@PathVariable UUID id) {
		Speaker speaker = requireSpeaker(id);
		Talk talk = firstTalk(speaker);
		String html = renderTemplate("banner/speakerSocial", speaker, talk);
		return htmlToPngConverter.convertToPng(html);
	}

	@GetMapping(value = "/talk-banner/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public byte[] talkBannerPng(@PathVariable Long id) {
		Talk talk = requireTalk(id);
		String html = renderTemplate("banner/talkBanner", null, talk);
		return htmlToPngConverter.convertToPng(html);
	}

	@GetMapping("/speaker-photo/{id}")
	public ResponseEntity<byte[]> speakerPhoto(@PathVariable UUID id) {
		requireSpeaker(id); // ensure speaker exists

		String[] exts = { ".jpg", ".png", ".jpeg" };
		for (String ext : exts) {
			String path = "/static/images/speaker/" + id + ext;
			try (InputStream in = getClass().getResourceAsStream(path)) {
				if (in != null) {
					byte[] bytes = in.readAllBytes();
					String mimeType = ext.equals(".png") ? "image/png" : "image/jpeg";
					return ResponseEntity.ok().contentType(MediaType.parseMediaType(mimeType)).body(bytes);
				}
			}
			catch (Exception e) {
				// try next extension
			}
		}

		// Redirect to placeholder
		return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/static/images/duke_cool.png").build();
	}

	@GetMapping(value = "/api/banners/generate-all", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public BannerGenerationResult generateAllBanners(@RequestParam(required = false) String outputDir) {
		if (outputDir != null && !outputDir.isBlank()) {
			Path base = Paths.get("").toAbsolutePath();
			Path resolved = base.resolve(outputDir.trim()).normalize();
			if (!resolved.startsWith(base)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Invalid output directory: must be within the working directory");
			}
			return bannerService.generateAllBanners(resolved.toString());
		}
		return bannerService.generateAllSpeakerBanners();
	}

	@PostMapping(value = "/api/banners/generate-speakers", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public BannerGenerationResult generateSpecificBanners(@RequestBody List<UUID> speakerIds) {
		return bannerService.generateSpeakerBanners(speakerIds);
	}

	private Speaker requireSpeaker(UUID id) {
		return speakerRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + id));
	}

	private Talk requireTalk(Long id) {
		return talkRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + id));
	}

	private static Talk firstTalk(Speaker speaker) {
		if (speaker.talks != null && !speaker.talks.isEmpty()) {
			return speaker.talks.get(0);
		}
		return null;
	}

	private String renderTemplate(String viewName, Speaker speaker, Talk talk) {
		Context ctx = new Context();
		ctx.setVariable("speaker", speaker);
		ctx.setVariable("talk", talk);
		ctx.setVariable("event", eventConfig);
		ctx.setVariable("utils", templateUtils);
		return templateEngine.process(viewName, ctx);
	}

}
