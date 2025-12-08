package org.acme.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BannerGenerationResult {
    
    private final List<SuccessEntry> successes = new ArrayList<>();
    private final List<FailureEntry> failures = new ArrayList<>();
    private final List<String> savedFiles = new ArrayList<>();
    
    public void addSuccess(UUID speakerId, String speakerName, int bannerSize) {
        successes.add(new SuccessEntry(speakerId, speakerName, bannerSize));
    }
    
    public void addFailure(UUID speakerId, String speakerName, String errorMessage) {
        failures.add(new FailureEntry(speakerId, speakerName, errorMessage));
    }
    
    public void addSavedFile(String filePath) {
        savedFiles.add(filePath);
    }
    
    public int getSuccessCount() {
        return successes.size();
    }
    
    public int getFailureCount() {
        return failures.size();
    }
    
    public List<SuccessEntry> getSuccesses() {
        return new ArrayList<>(successes);
    }
    
    public List<FailureEntry> getFailures() {
        return new ArrayList<>(failures);
    }
    
    public List<String> getSavedFiles() {
        return new ArrayList<>(savedFiles);
    }
    
    public boolean hasFailures() {
        return !failures.isEmpty();
    }
    
    public boolean isAllSuccessful() {
        return failures.isEmpty() && !successes.isEmpty();
    }
    
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Banner Generation Summary:\n"));
        summary.append(String.format("- Successful: %d\n", getSuccessCount()));
        summary.append(String.format("- Failed: %d\n", getFailureCount()));
        
        if (!savedFiles.isEmpty()) {
            summary.append(String.format("- Files saved: %d\n", savedFiles.size()));
        }
        
        if (hasFailures()) {
            summary.append("\nFailures:\n");
            for (FailureEntry failure : failures) {
                summary.append(String.format("- %s (%s): %s\n", 
                    failure.speakerName, failure.speakerId, failure.errorMessage));
            }
        }
        
        return summary.toString();
    }
    
    public static class SuccessEntry {
        public final UUID speakerId;
        public final String speakerName;
        public final int bannerSize;
        
        public SuccessEntry(UUID speakerId, String speakerName, int bannerSize) {
            this.speakerId = speakerId;
            this.speakerName = speakerName;
            this.bannerSize = bannerSize;
        }
    }
    
    public static class FailureEntry {
        public final UUID speakerId;
        public final String speakerName;
        public final String errorMessage;
        
        public FailureEntry(UUID speakerId, String speakerName, String errorMessage) {
            this.speakerId = speakerId;
            this.speakerName = speakerName;
            this.errorMessage = errorMessage;
        }
    }
}

// Made with Bob
