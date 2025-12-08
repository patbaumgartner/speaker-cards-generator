package org.acme.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.acme.model.Speaker;
import org.acme.model.Talk;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BannerGenerationService {
    
    private static final Logger LOG = Logger.getLogger(BannerGenerationService.class);
    
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int httpPort;
    
    @ConfigProperty(name = "quarkus.http.host", defaultValue = "localhost")
    String httpHost;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    /**
     * Generate banners for all speakers in the database
     */
    @Transactional
    public BannerGenerationResult generateAllSpeakerBanners() {
        List<Speaker> speakers = Speaker.listAll();
        LOG.infof("Starting banner generation for %d speakers", speakers.size());
        
        BannerGenerationResult result = new BannerGenerationResult();
        
        for (Speaker speaker : speakers) {
            try {
                byte[] bannerData = generateSpeakerBannerData(speaker);
                result.addSuccess(speaker.id, speaker.toString(), bannerData.length);
                LOG.infof("Generated banner for %s (%s)", speaker.toString(), speaker.id);
            } catch (Exception e) {
                result.addFailure(speaker.id, speaker.toString(), e.getMessage());
                LOG.errorf(e, "Failed to generate banner for %s (%s)", speaker.toString(), speaker.id);
            }
        }
        
        LOG.infof("Banner generation completed: %d successful, %d failed", 
                 result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * Generate banners for specific speaker IDs
     */
    @Transactional
    public BannerGenerationResult generateSpeakerBanners(List<UUID> speakerIds) {
        LOG.infof("Starting banner generation for %d specific speakers", speakerIds.size());
        
        BannerGenerationResult result = new BannerGenerationResult();
        
        for (UUID speakerId : speakerIds) {
            Speaker speaker = Speaker.findById(speakerId);
            if (speaker == null) {
                result.addFailure(speakerId, "Unknown", "Speaker not found");
                continue;
            }
            
            try {
                byte[] bannerData = generateSpeakerBannerData(speaker);
                result.addSuccess(speaker.id, speaker.toString(), bannerData.length);
                LOG.infof("Generated banner for %s (%s)", speaker.toString(), speaker.id);
            } catch (Exception e) {
                result.addFailure(speaker.id, speaker.toString(), e.getMessage());
                LOG.errorf(e, "Failed to generate banner for %s (%s)", speaker.toString(), speaker.id);
            }
        }
        
        return result;
    }
    
    /**
     * Generate banners for all speakers and save to filesystem
     */
    @Transactional
    public BannerGenerationResult generateAndSaveAllBanners(String outputDirectory) {
        List<Speaker> speakers = Speaker.listAll();
        LOG.infof("Starting banner generation and save for %d speakers to %s", speakers.size(), outputDirectory);
        
        // Create output directory if it doesn't exist
        Path outputPath = Paths.get(outputDirectory);
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + outputDirectory, e);
        }
        
        BannerGenerationResult result = new BannerGenerationResult();
        
        for (Speaker speaker : speakers) {
            try {
                byte[] bannerData = generateSpeakerBannerData(speaker);
                
                // Save to file
                String filename = String.format("%s_%s_%s.png", 
                    sanitizeFilename(speaker.firstName), 
                    sanitizeFilename(speaker.lastName), 
                    speaker.id.toString());
                Path filePath = outputPath.resolve(filename);
                
                Files.write(filePath, bannerData);
                
                result.addSuccess(speaker.id, speaker.toString(), bannerData.length);
                result.addSavedFile(filePath.toString());
                LOG.infof("Generated and saved banner for %s (%s) to %s", speaker.toString(), speaker.id, filePath);
            } catch (Exception e) {
                result.addFailure(speaker.id, speaker.toString(), e.getMessage());
                LOG.errorf(e, "Failed to generate banner for %s (%s)", speaker.toString(), speaker.id);
            }
        }
        
        return result;
    }
    
    /**
     * Generate all banners (speaker banners, talk banners, and speaker social banners) and save to filesystem
     * 
     * @param outputDirectory Base output directory
     * @return BannerGenerationResult with all generation results
     */
    @Transactional
    public BannerGenerationResult generateAllBanners(String outputDirectory) {
        List<Speaker> speakers = Speaker.listAll();
        List<Talk> talks = Talk.listAll();
        
        LOG.infof("Starting banner generation for %d speakers and %d talks to %s", 
                 speakers.size(), talks.size(), outputDirectory);
        
        // Create output directories if they don't exist
        Path outputPath = Paths.get(outputDirectory);
        Path speakerDir = outputPath.resolve("speaker");
        Path talksDir = outputPath.resolve("talks");
        Path socialDir = outputPath.resolve("social");
        
        try {
            Files.createDirectories(speakerDir);
            Files.createDirectories(talksDir);
            Files.createDirectories(socialDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directories: " + outputDirectory, e);
        }
        
        BannerGenerationResult result = new BannerGenerationResult();
        
        // Generate speaker banners: outputDir/speaker/<speakerID>.png
        for (Speaker speaker : speakers) {
            try {
                byte[] bannerData = generateSpeakerBannerData(speaker);
                Path filePath = speakerDir.resolve(speaker.id.toString() + ".png");
                Files.write(filePath, bannerData);
                
                result.addSuccess(speaker.id, speaker.toString(), bannerData.length);
                result.addSavedFile(filePath.toString());
                LOG.infof("Generated speaker banner for %s (%s) to %s", speaker.toString(), speaker.id, filePath);
            } catch (Exception e) {
                result.addFailure(speaker.id, speaker.toString(), e.getMessage());
                LOG.errorf(e, "Failed to generate speaker banner for %s (%s)", speaker.toString(), speaker.id);
            }
        }
        
        // Generate talk banners: outputDir/talks/<talkID>.png
        for (Talk talk : talks) {
            try {
                byte[] bannerData = generateTalkBannerData(talk);
                Path filePath = talksDir.resolve(talk.id.toString() + ".png");
                Files.write(filePath, bannerData);
                
                result.addSuccess(null, talk.title, bannerData.length);
                result.addSavedFile(filePath.toString());
                LOG.infof("Generated talk banner for %s (%d) to %s", talk.title, talk.id, filePath);
            } catch (Exception e) {
                result.addFailure(null, talk.title, e.getMessage());
                LOG.errorf(e, "Failed to generate talk banner for %s (%d)", talk.title, talk.id);
            }
        }
        
        // Generate speaker social banners: outputDir/social/<speaker.lastName>_<speaker.firstName>.png
        for (Speaker speaker : speakers) {
            try {
                byte[] bannerData = generateSpeakerSocialBannerData(speaker);
                String filename = String.format("%s_%s.png", 
                    sanitizeFilename(speaker.lastName), 
                    sanitizeFilename(speaker.firstName));
                Path filePath = socialDir.resolve(filename);
                Files.write(filePath, bannerData);
                
                result.addSuccess(speaker.id, speaker.toString(), bannerData.length);
                result.addSavedFile(filePath.toString());
                LOG.infof("Generated social banner for %s (%s) to %s", speaker.toString(), speaker.id, filePath);
            } catch (Exception e) {
                result.addFailure(speaker.id, speaker.toString(), e.getMessage());
                LOG.errorf(e, "Failed to generate social banner for %s (%s)", speaker.toString(), speaker.id);
            }
        }
        
        LOG.infof("Banner generation completed: %d successful, %d failed", 
                 result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * Generate banners asynchronously for better performance
     */
    @Transactional
    public CompletableFuture<BannerGenerationResult> generateAllSpeakerBannersAsync() {
        return CompletableFuture.supplyAsync(() -> generateAllSpeakerBanners());
    }
    
    /**
     * Generate banner data for a single speaker by calling the existing PNG endpoint
     */
    private byte[] generateSpeakerBannerData(Speaker speaker) throws Exception {
        String url = String.format("http://%s:%d/speaker-banner/%s.png", httpHost, httpPort, speaker.id);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format("Failed to generate banner for speaker %s. HTTP %d",
                speaker.id, response.statusCode()));
        }
        
        return response.body();
    }
    
    /**
     * Generate banner data for a single talk by calling the existing PNG endpoint
     */
    private byte[] generateTalkBannerData(Talk talk) throws Exception {
        String url = String.format("http://%s:%d/talk-banner/%d.png", httpHost, httpPort, talk.id);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format("Failed to generate banner for talk %d. HTTP %d",
                talk.id, response.statusCode()));
        }
        
        return response.body();
    }
    
    /**
     * Generate social banner data for a single speaker by calling the existing PNG endpoint
     */
    private byte[] generateSpeakerSocialBannerData(Speaker speaker) throws Exception {
        String url = String.format("http://%s:%d/speaker-social/%s.png", httpHost, httpPort, speaker.id);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format("Failed to generate social banner for speaker %s. HTTP %d",
                speaker.id, response.statusCode()));
        }
        
        return response.body();
    }
    
    /**
     * Sanitize filename to remove invalid characters
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Generate banners in parallel for better performance
     */
    @Transactional
    public BannerGenerationResult generateAllSpeakerBannersParallel() {
        List<Speaker> speakers = Speaker.listAll();
        LOG.infof("Starting parallel banner generation for %d speakers", speakers.size());
        
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(speakers.size(), Runtime.getRuntime().availableProcessors()));
        
        BannerGenerationResult result = new BannerGenerationResult();
        
        try {
            List<CompletableFuture<Void>> futures = speakers.stream()
                .map(speaker -> CompletableFuture.runAsync(() -> {
                    try {
                        byte[] bannerData = generateSpeakerBannerData(speaker);
                        synchronized (result) {
                            result.addSuccess(speaker.id, speaker.toString(), bannerData.length);
                        }
                        LOG.infof("Generated banner for %s (%s)", speaker.toString(), speaker.id);
                    } catch (Exception e) {
                        synchronized (result) {
                            result.addFailure(speaker.id, speaker.toString(), e.getMessage());
                        }
                        LOG.errorf(e, "Failed to generate banner for %s (%s)", speaker.toString(), speaker.id);
                    }
                }, executor))
                .collect(Collectors.toList());
            
            // Wait for all tasks to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
        } finally {
            executor.shutdown();
        }
        
        LOG.infof("Parallel banner generation completed: %d successful, %d failed", 
                 result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
}

