package com.fortytwotalents.controller;

import java.io.InputStream;
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

import com.fortytwotalents.config.EventConfig;
import com.fortytwotalents.model.Speaker;
import com.fortytwotalents.model.Talk;
import com.fortytwotalents.repository.SpeakerRepository;
import com.fortytwotalents.repository.TalkRepository;
import com.fortytwotalents.service.BannerGenerationResult;
import com.fortytwotalents.service.BannerGenerationService;
import com.fortytwotalents.util.HtmlToPngConverter;
import com.fortytwotalents.util.TemplateUtils;

/**
 * Spring MVC controller for banner HTML preview and PNG generation.
 *
 * <p>HTML preview endpoints return Thymeleaf-rendered HTML (useful for
 * development / tweaking templates).  PNG endpoints convert the same HTML
 * to a raster image using OpenHTMLtoPDF and return the bytes with
 * {@code Content-Type: image/png}.
 *
 * <h2>Endpoints</h2>
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

    public BannerController(SpeakerRepository speakerRepository,
                            TalkRepository talkRepository,
                            BannerGenerationService bannerService,
                            HtmlToPngConverter htmlToPngConverter,
                            SpringTemplateEngine templateEngine,
                            EventConfig eventConfig,
                            TemplateUtils templateUtils) {
        this.speakerRepository = speakerRepository;
        this.talkRepository = talkRepository;
        this.bannerService = bannerService;
        this.htmlToPngConverter = htmlToPngConverter;
        this.templateEngine = templateEngine;
        this.eventConfig = eventConfig;
        this.templateUtils = templateUtils;
    }

    // -------------------------------------------------------------------------
    // HTML preview endpoints
    // -------------------------------------------------------------------------

    /**
     * Renders a speaker banner as an HTML page (browser preview).
     *
     * @param id    speaker UUID
     * @param model Spring MVC model
     * @return Thymeleaf view name {@code banner/speakerBanner}
     */
    @GetMapping("/speaker-banner/{id}")
    public String speakerBanner(@PathVariable UUID id, Model model) {
        Speaker speaker = requireSpeaker(id);
        Talk talk = firstTalk(speaker);
        model.addAttribute("speaker", speaker);
        model.addAttribute("talk", talk);
        model.addAttribute("event", eventConfig);
        return "banner/speakerBanner";
    }

    /**
     * Renders a talk banner as an HTML page (browser preview).
     *
     * @param id    talk / session ID
     * @param model Spring MVC model
     * @return Thymeleaf view name {@code banner/talkBanner}
     */
    @GetMapping("/talk-banner/{id}")
    public String talkBanner(@PathVariable Long id, Model model) {
        Talk talk = requireTalk(id);
        model.addAttribute("talk", talk);
        model.addAttribute("event", eventConfig);
        return "banner/talkBanner";
    }

    // -------------------------------------------------------------------------
    // PNG download endpoints
    // -------------------------------------------------------------------------

    /**
     * Generates a speaker banner PNG and returns it as {@code image/png}.
     *
     * @param id speaker UUID
     * @return PNG byte array response
     */
    @GetMapping(value = "/speaker-banner/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] speakerBannerPng(@PathVariable UUID id) {
        Speaker speaker = requireSpeaker(id);
        Talk talk = firstTalk(speaker);
        String html = renderTemplate("banner/speakerBanner", speaker, talk);
        return htmlToPngConverter.convertToPng(html);
    }

    /**
     * Generates a speaker social-media banner PNG (square, 1080×1080).
     *
     * @param id speaker UUID
     * @return PNG byte array response
     */
    @GetMapping(value = "/speaker-social/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] speakerSocialPng(@PathVariable UUID id) {
        Speaker speaker = requireSpeaker(id);
        Talk talk = firstTalk(speaker);
        String html = renderTemplate("banner/speakerSocial", speaker, talk);
        return htmlToPngConverter.convertToPng(html);
    }

    /**
     * Generates a talk banner PNG.
     *
     * @param id talk / session ID
     * @return PNG byte array response
     */
    @GetMapping(value = "/talk-banner/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] talkBannerPng(@PathVariable Long id) {
        Talk talk = requireTalk(id);
        String html = renderTemplate("banner/talkBanner", null, talk);
        return htmlToPngConverter.convertToPng(html);
    }

    // -------------------------------------------------------------------------
    // Speaker photo endpoint
    // -------------------------------------------------------------------------

    /**
     * Serves a speaker's profile photo.
     *
     * <p>Looks for the photo in
     * {@code /static/images/speaker/{id}.{jpg|png|jpeg}} on the classpath.
     * If no photo is found the client is redirected to the default placeholder
     * image.
     *
     * @param id speaker UUID
     * @return photo response or redirect
     */
    @GetMapping("/speaker-photo/{id}")
    public ResponseEntity<byte[]> speakerPhoto(@PathVariable UUID id) {
        requireSpeaker(id); // ensure speaker exists

        String[] exts = {".jpg", ".png", ".jpeg"};
        for (String ext : exts) {
            String path = "/static/images/speaker/" + id + ext;
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in != null) {
                    byte[] bytes = in.readAllBytes();
                    String mimeType = ext.equals(".png") ? "image/png" : "image/jpeg";
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(mimeType))
                            .body(bytes);
                }
            } catch (Exception e) {
                // try next extension
            }
        }

        // Redirect to placeholder
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/static/images/duke_cool.png")
                .build();
    }

    // -------------------------------------------------------------------------
    // Bulk generation API
    // -------------------------------------------------------------------------

    /**
     * Generates all banner types for all speakers and (optionally) saves them
     * to the file system.
     *
     * @param outputDir optional file-system path where PNGs will be written
     * @return generation result summary as JSON
     */
    @GetMapping(value = "/api/banners/generate-all", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public BannerGenerationResult generateAllBanners(
            @RequestParam(required = false) String outputDir) {
        if (outputDir != null && !outputDir.isBlank()) {
            return bannerService.generateAllBanners(outputDir.trim());
        }
        return bannerService.generateAllSpeakerBanners();
    }

    /**
     * Generates speaker banners for the given list of speaker UUIDs.
     *
     * @param speakerIds list of speaker UUIDs in the JSON request body
     * @return generation result summary as JSON
     */
    @PostMapping(value = "/api/banners/generate-speakers",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public BannerGenerationResult generateSpecificBanners(@RequestBody List<UUID> speakerIds) {
        return bannerService.generateSpeakerBanners(speakerIds);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Speaker requireSpeaker(UUID id) {
        return speakerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Speaker not found: " + id));
    }

    private Talk requireTalk(Long id) {
        return talkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Talk not found: " + id));
    }

    private static Talk firstTalk(Speaker speaker) {
        if (speaker.talks != null && !speaker.talks.isEmpty()) {
            return speaker.talks.get(0);
        }
        return null;
    }

    /**
     * Renders a Thymeleaf banner template to an HTML string.
     *
     * @param viewName Thymeleaf template path (e.g. {@code banner/speakerBanner})
     * @param speaker  speaker model object (may be {@code null} for talk banners)
     * @param talk     talk model object (may be {@code null})
     * @return rendered HTML string
     */
    private String renderTemplate(String viewName, Speaker speaker, Talk talk) {
        Context ctx = new Context();
        ctx.setVariable("speaker", speaker);
        ctx.setVariable("talk", talk);
        ctx.setVariable("event", eventConfig);
        ctx.setVariable("utils", templateUtils);
        return templateEngine.process(viewName, ctx);
    }
}
