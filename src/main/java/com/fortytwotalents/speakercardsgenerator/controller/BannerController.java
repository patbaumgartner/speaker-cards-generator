package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
import com.fortytwotalents.speakercardsgenerator.service.BannerGenerationResult;
import com.fortytwotalents.speakercardsgenerator.service.BannerGenerationService;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer.BannerType;
import com.fortytwotalents.speakercardsgenerator.service.SpeakerPhotoStore;
import com.fortytwotalents.speakercardsgenerator.util.TemplateUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

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

	private static final String PLACEHOLDER_PHOTO = "/static/images/duke_cool.png";

	private final SpeakerRepository speakerRepository;

	private final TalkRepository talkRepository;

	private final BannerGenerationService bannerService;

	private final BannerRenderer renderer;

	private final EventConfig eventConfig;

	private final TemplateUtils templateUtils;

	private final SpeakerPhotoStore photoStore;

	public BannerController(SpeakerRepository speakerRepository, TalkRepository talkRepository,
			BannerGenerationService bannerService, BannerRenderer renderer, EventConfig eventConfig,
			TemplateUtils templateUtils, SpeakerPhotoStore photoStore) {
		this.speakerRepository = speakerRepository;
		this.talkRepository = talkRepository;
		this.bannerService = bannerService;
		this.renderer = renderer;
		this.eventConfig = eventConfig;
		this.templateUtils = templateUtils;
		this.photoStore = photoStore;
	}

	@GetMapping("/speaker-banner/{id}")
	public String speakerBanner(@PathVariable UUID id, Model model) {
		return speakerPreview(BannerType.SPEAKER, id, model);
	}

	@GetMapping("/speaker-social/{id}")
	public String speakerSocial(@PathVariable UUID id, Model model) {
		return speakerPreview(BannerType.SOCIAL, id, model);
	}

	@GetMapping("/talk-banner/{id}")
	public String talkBanner(@PathVariable Long id, Model model) {
		Talk talk = requireTalk(id);
		model.addAttribute("talk", talk);
		model.addAttribute("event", eventConfig);
		model.addAttribute("utils", templateUtils);
		return BannerType.TALK.view();
	}

	@GetMapping(value = "/speaker-banner/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public byte[] speakerBannerPng(@PathVariable UUID id) {
		return renderer.renderSpeakerPng(BannerType.SPEAKER, requireSpeaker(id));
	}

	@GetMapping(value = "/speaker-social/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public byte[] speakerSocialPng(@PathVariable UUID id) {
		return renderer.renderSpeakerPng(BannerType.SOCIAL, requireSpeaker(id));
	}

	@GetMapping(value = "/talk-banner/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public byte[] talkBannerPng(@PathVariable Long id) {
		return renderer.renderPng(BannerType.TALK, null, requireTalk(id));
	}

	private String speakerPreview(BannerType type, UUID id, Model model) {
		Speaker speaker = requireSpeaker(id);
		model.addAttribute("speaker", speaker);
		model.addAttribute("talk", BannerRenderer.firstTalk(speaker));
		model.addAttribute("event", eventConfig);
		model.addAttribute("utils", templateUtils);
		return type.view();
	}

	@GetMapping("/speaker-photo/{id}")
	public ResponseEntity<byte[]> speakerPhoto(@PathVariable UUID id) {
		requireSpeaker(id); // ensure speaker exists
		SpeakerPhotoStore.Photo photo = photoStore.find(id).orElseGet(BannerController::placeholderPhoto);
		return ResponseEntity.ok()
			.contentType(photo.contentType())
			.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
			.body(photo.content());
	}

	/**
	 * Served inline rather than as a redirect: the banner renderer resolves this URL
	 * while rasterising a page and must get image bytes back in a single hop.
	 */
	private static SpeakerPhotoStore.Photo placeholderPhoto() {
		try (InputStream in = BannerController.class.getResourceAsStream(PLACEHOLDER_PHOTO)) {
			if (in == null) {
				throw new IllegalStateException("Missing bundled placeholder image " + PLACEHOLDER_PHOTO);
			}
			return new SpeakerPhotoStore.Photo(in.readAllBytes(), MediaType.IMAGE_PNG);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not read bundled placeholder image " + PLACEHOLDER_PHOTO, ex);
		}
	}

	@GetMapping(value = "/api/banners/generate-all", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public BannerGenerationResult generateAllBanners(@RequestParam(required = false) String outputDir) {
		if (outputDir != null && !outputDir.isBlank()) {
			Path base = Path.of("").toAbsolutePath();
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

	@PutMapping(value = "/api/talks/{id}/formatted-title", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, String> updateFormattedTitle(@PathVariable Long id, @RequestBody Map<String, String> body) {
		Talk talk = requireTalk(id);
		String raw = body.get("formattedTitle");
		String sanitized = TemplateUtils.sanitizeFormattedTitle(raw);
		talk.setFormattedTitle(sanitized);
		talkRepository.save(talk);
		return Map.of("formattedTitle", sanitized != null ? sanitized : "");
	}

	@GetMapping("/api/banners/download/all")
	public void downloadAllBanners(HttpServletResponse response) throws IOException {
		streamZip(response, "banners-all.zip", EnumSet.allOf(BannerType.class));
	}

	@GetMapping("/api/banners/download/speakers")
	public void downloadSpeakerBanners(HttpServletResponse response) throws IOException {
		streamZip(response, "banners-speakers.zip", EnumSet.of(BannerType.SPEAKER));
	}

	@GetMapping("/api/banners/download/talks")
	public void downloadTalkBanners(HttpServletResponse response) throws IOException {
		streamZip(response, "banners-talks.zip", EnumSet.of(BannerType.TALK));
	}

	@GetMapping("/api/banners/download/social")
	public void downloadSocialBanners(HttpServletResponse response) throws IOException {
		streamZip(response, "banners-social.zip", EnumSet.of(BannerType.SOCIAL));
	}

	private void streamZip(HttpServletResponse response, String filename, EnumSet<BannerType> types)
			throws IOException {
		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		bannerService.writeZip(response.getOutputStream(), types);
	}

	private Speaker requireSpeaker(UUID id) {
		return speakerRepository.findWithTalksById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + id));
	}

	private Talk requireTalk(Long id) {
		return talkRepository.findWithSpeakersById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + id));
	}

}
