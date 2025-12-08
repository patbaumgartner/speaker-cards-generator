package org.acme.rest;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.acme.model.Speaker;
import org.acme.model.Talk;
import org.acme.service.BannerGenerationResult;
import org.acme.service.BannerGenerationService;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.renarde.Controller;
import io.quarkiverse.renarde.pdf.Pdf;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

public class Banner extends Controller {

    @Inject
    BannerGenerationService bannerService;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance speakerBanner(Speaker speaker, Talk talk);

        public static native TemplateInstance talkBanner(Talk talk);

        public static native TemplateInstance speakerSocial(Speaker speaker, Talk talk);
    }

    @Path("/speaker-banner")
    @Transactional
    public TemplateInstance speakerBanner(@RestPath UUID id) {
        Speaker speaker = Speaker.findById(id);
        notFoundIfNull(speaker);
        // Load talks relationship
        Talk talk = null;
        if (speaker.talks != null && !speaker.talks.isEmpty()) {
            talk = speaker.talks.get(0);
        }
        return Templates.speakerBanner(speaker, talk);
    }

    @Produces(Pdf.IMAGE_PNG)
    @Path("/speaker-banner/{id}.png")
    @Transactional
    public TemplateInstance banner(@RestPath UUID id) {
        Speaker speaker = Speaker.findById(id);
        notFoundIfNull(speaker);
        // Load talks relationship
        Talk talk = null;
        if (speaker.talks != null && !speaker.talks.isEmpty()) {
            talk = speaker.talks.get(0);
        }
        return Templates.speakerBanner(speaker, talk);
    }

    @Produces(Pdf.IMAGE_PNG)
    @Path("/speaker-social/{id}.png")
    @Transactional
    public TemplateInstance speakerSocialBanner(@RestPath UUID id) {
        Speaker speaker = Speaker.findById(id);
        notFoundIfNull(speaker);
        // Load talks relationship
        Talk talk = null;
        if (speaker.talks != null && !speaker.talks.isEmpty()) {
            talk = speaker.talks.get(0);
        }
        return Templates.speakerSocial(speaker, talk);
    }

    @Path("/talk-banner")
    @Transactional
    public TemplateInstance talkBanner(@RestPath Long id) {
        Talk talk = Talk.findById(id);
        notFoundIfNull(talk);
        // Ensure speakers are loaded by accessing the list
        if (talk.speakers != null) {
            // Force lazy loading by iterating
            for (Speaker speaker : talk.speakers) {
                speaker.id.toString(); // Access a field to ensure it's loaded
            }
        }
        return Templates.talkBanner(talk);
    }

    @Produces(Pdf.IMAGE_PNG)
    @Path("/talk-banner/{id}.png")
    @Transactional
    public TemplateInstance talkBannerPng(@RestPath Long id) {
        Talk talk = Talk.findById(id);
        notFoundIfNull(talk);
        // Ensure speakers are loaded by accessing the list
        if (talk.speakers != null) {
            // Force lazy loading by iterating
            for (Speaker speaker : talk.speakers) {
                speaker.id.toString(); // Access a field to ensure it's loaded
            }
        }
        return Templates.talkBanner(talk);
    }

    @GET
    @Path("/speaker-photo/{id}")
    @Produces({"image/png", "image/jpeg"})
    public Response speakerPhoto(@RestPath UUID id, Request request) {
        Speaker speaker = Speaker.findById(id);
        notFoundIfNull(speaker);

        // Try to find speaker image in resources/META-INF/speaker/{id}.{ext}
        String[] extensions = { ".jpg", ".png", ".jpeg" };
        String resourcePath = null;
        String mimeType = null;

        for (String ext : extensions) {
            String testPath = "/META-INF/speaker/" + id + ext;
            InputStream testStream = getClass().getResourceAsStream(testPath);
            if (testStream != null) {
                try {
                    testStream.close();
                } catch (Exception e) {
                    // Ignore
                }
                resourcePath = testPath;
                // Determine MIME type based on extension
                if (ext.equals(".png")) {
                    mimeType = "image/png";
                } else {
                    mimeType = "image/jpeg";
                }
                break;
            }
        }

        // If no image found, fall back to duke_cool.png
        if (resourcePath == null) {
            seeOther("/static/images/duke_cool.png");
            return null; // seeOther will redirect
        }

        // Read the image file
        try (InputStream imageStream = getClass().getResourceAsStream(resourcePath)) {
            if (imageStream == null) {
                seeOther("/static/images/duke_cool.png");
                return null;
            }
            byte[] bytes = imageStream.readAllBytes();
            return Response.ok(bytes, mimeType).build();
        } catch (Exception e) {
            throw new RuntimeException("Error reading speaker image", e);
        }
    }

    

    @GET
    @Path("/api/banners/generate-all")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public BannerGenerationResult generateAllBanners(@RestQuery String outputDir) {
        if (outputDir != null && !outputDir.trim().isEmpty()) {
            return bannerService.generateAllBanners(outputDir.trim());
        } else {
            return bannerService.generateAllSpeakerBanners();
        }
    }

   

    @POST
    @Path("/api/banners/generate-speakers")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public BannerGenerationResult generateSpecificBanners(List<UUID> speakerIds) {
        return bannerService.generateSpeakerBanners(speakerIds);
    }

}
