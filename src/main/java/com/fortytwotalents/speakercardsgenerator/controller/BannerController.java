package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Session;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.SessionRepository;
import com.fortytwotalents.speakercardsgenerator.service.BannerGenerationResult;
import com.fortytwotalents.speakercardsgenerator.service.BannerGenerationService;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer.BannerType;
import com.fortytwotalents.speakercardsgenerator.service.SpeakerPhotoStore;
import com.fortytwotalents.speakercardsgenerator.util.TemplateUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
 *   GET  /session-banner/{id}        – HTML preview of a session banner
 *   GET  /session-banner/{id}.png    – PNG download of a session banner
 *   GET  /speaker-photo              – Branded default speaker avatar
 *   GET  /speaker-photo/{id}         – Speaker profile photo (default avatar if missing)
 *   GET  /api/banners/generate-all   – Bulk-generate all banners (optional ?outputDir=…)
 *   POST /api/banners/generate-speakers – Bulk-generate banners for selected speaker IDs
 * </pre>
 */
@Controller
public class BannerController {

	private final SpeakerRepository speakerRepository;

	private final SessionRepository sessionRepository;

	private final BannerGenerationService bannerService;

	private final BannerRenderer renderer;

	private final EventConfig eventConfig;

	private final TemplateUtils templateUtils;

	private final SpeakerPhotoStore photoStore;

	public BannerController(SpeakerRepository speakerRepository, SessionRepository sessionRepository,
			BannerGenerationService bannerService, BannerRenderer renderer, EventConfig eventConfig,
			TemplateUtils templateUtils, SpeakerPhotoStore photoStore) {
		this.speakerRepository = speakerRepository;
		this.sessionRepository = sessionRepository;
		this.bannerService = bannerService;
		this.renderer = renderer;
		this.eventConfig = eventConfig;
		this.templateUtils = templateUtils;
		this.photoStore = photoStore;
	}

	@GetMapping("/speaker-banner/{id}")
	public String speakerBanner(@PathVariable UUID id, @RequestParam(required = false) Long sessionId, Model model) {
		return speakerPreview(BannerType.SPEAKER, id, sessionId, model);
	}

	@GetMapping("/speaker-social/{id}")
	public String speakerSocial(@PathVariable UUID id, @RequestParam(required = false) Long sessionId, Model model) {
		return speakerPreview(BannerType.SOCIAL, id, sessionId, model);
	}

	@GetMapping("/session-banner/{id}")
	public String sessionBanner(@PathVariable Long id, Model model) {
		Session session = requireSession(id);
		model.addAttribute("conferenceSession", session);
		model.addAttribute("event", eventConfig);
		model.addAttribute("utils", templateUtils);
		return BannerType.SESSION.view();
	}

	@GetMapping(value = "/speaker-banner/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> speakerBannerPng(@PathVariable UUID id,
			@RequestParam(required = false) Long sessionId) {
		Speaker speaker = requireSpeaker(id);
		return bannerResponse(BannerType.SPEAKER, speaker, speakerSession(speaker, sessionId));
	}

	@GetMapping(value = "/speaker-social/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> speakerSocialPng(@PathVariable UUID id,
			@RequestParam(required = false) Long sessionId) {
		Speaker speaker = requireSpeaker(id);
		return bannerResponse(BannerType.SOCIAL, speaker, speakerSession(speaker, sessionId));
	}

	@GetMapping(value = "/session-banner/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> sessionBannerPng(@PathVariable Long id) {
		return bannerResponse(BannerType.SESSION, null, requireSession(id));
	}

	private ResponseEntity<byte[]> bannerResponse(BannerType type, Speaker speaker, Session session) {
		String disposition = ContentDisposition.attachment()
			.filename(renderer.downloadFileName(type, speaker, session))
			.build()
			.toString();
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
			.body(renderer.renderPng(type, speaker, session));
	}

	private String speakerPreview(BannerType type, UUID id, Long sessionId, Model model) {
		Speaker speaker = requireSpeaker(id);
		model.addAttribute("speaker", speaker);
		model.addAttribute("conferenceSession", speakerSession(speaker, sessionId));
		model.addAttribute("event", eventConfig);
		model.addAttribute("utils", templateUtils);
		return type.view();
	}

	private static Session speakerSession(Speaker speaker, Long sessionId) {
		if (sessionId == null) {
			return BannerRenderer.firstSession(speaker);
		}
		return speaker.getSessions()
			.stream()
			.filter(session -> sessionId.equals(session.getId()))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
					"Session " + sessionId + " does not belong to speaker " + speaker.getId()));
	}

	@GetMapping("/speaker-photo")
	public ResponseEntity<byte[]> defaultSpeakerPhoto() {
		return photoResponse(SpeakerPhotoStore.defaultPhoto());
	}

	@GetMapping("/speaker-photo/{id}")
	public ResponseEntity<byte[]> speakerPhoto(@PathVariable UUID id) {
		requireSpeaker(id); // ensure speaker exists
		return photoResponse(photoStore.findOrPlaceholder(id));
	}

	/**
	 * Served inline rather than as a redirect: the banner renderer resolves this URL
	 * while rasterising a page and must get image bytes back in a single hop.
	 */
	private static ResponseEntity<byte[]> photoResponse(SpeakerPhotoStore.Photo photo) {
		return ResponseEntity.ok()
			.contentType(photo.contentType())
			.cacheControl(CacheControl.noCache())
			.body(photo.content());
	}

	@PostMapping(value = "/api/banners/generate-all", produces = MediaType.APPLICATION_JSON_VALUE)
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

	@PutMapping(value = "/api/sessions/{id}/formatted-title", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, String> updateFormattedTitle(@PathVariable Long id, @RequestBody Map<String, String> body) {
		Session session = requireSession(id);
		String raw = body.get("formattedTitle");
		String sanitized = TemplateUtils.sanitizeFormattedTitle(raw);
		session.setFormattedTitle(sanitized);
		sessionRepository.save(session);
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

	@GetMapping("/api/banners/download/sessions")
	public void downloadSessionBanners(HttpServletResponse response) throws IOException {
		streamZip(response, "banners-sessions.zip", EnumSet.of(BannerType.SESSION));
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
		return speakerRepository.findWithSessionsById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + id));
	}

	private Session requireSession(Long id) {
		return sessionRepository.findWithSpeakersById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + id));
	}

}
