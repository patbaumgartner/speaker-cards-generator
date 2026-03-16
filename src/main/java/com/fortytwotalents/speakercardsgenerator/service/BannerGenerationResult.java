package com.fortytwotalents.speakercardsgenerator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Holds the result of a bulk banner generation run.
 *
 * <p>
 * Tracks which speakers/talks were processed successfully, which failed, and the list of
 * files saved to the output directory (if requested).
 */
public class BannerGenerationResult {

	private final List<SuccessEntry> successes = new ArrayList<>();

	private final List<FailureEntry> failures = new ArrayList<>();

	private final List<String> savedFiles = new ArrayList<>();

	/**
	 * Records a successful banner generation.
	 * @param speakerId speaker UUID (may be {@code null} for talk-only banners)
	 * @param displayName human-readable name of the speaker or talk
	 * @param bannerSize size of the generated PNG in bytes
	 */
	public void addSuccess(UUID speakerId, String displayName, int bannerSize) {
		successes.add(new SuccessEntry(speakerId, displayName, bannerSize));
	}

	/**
	 * Records a failed banner generation.
	 * @param speakerId speaker UUID (may be {@code null})
	 * @param displayName human-readable name
	 * @param errorMessage description of the failure
	 */
	public void addFailure(UUID speakerId, String displayName, String errorMessage) {
		failures.add(new FailureEntry(speakerId, displayName, errorMessage));
	}

	/**
	 * Records the path of a saved banner file.
	 * @param filePath absolute path of the saved file
	 */
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
		return List.copyOf(successes);
	}

	public List<FailureEntry> getFailures() {
		return List.copyOf(failures);
	}

	public List<String> getSavedFiles() {
		return List.copyOf(savedFiles);
	}

	public boolean hasFailures() {
		return !failures.isEmpty();
	}

	public boolean isAllSuccessful() {
		return failures.isEmpty() && !successes.isEmpty();
	}

	/**
	 * Produces a human-readable summary of the generation run.
	 * @return multi-line summary string
	 */
	public String getSummary() {
		StringBuilder sb = new StringBuilder("Banner Generation Summary:\n");
		sb.append("- Successful: %d%n".formatted(getSuccessCount()));
		sb.append("- Failed: %d%n".formatted(getFailureCount()));
		if (!savedFiles.isEmpty()) {
			sb.append("- Files saved: %d%n".formatted(savedFiles.size()));
		}
		if (hasFailures()) {
			sb.append("\nFailures:\n");
			for (FailureEntry f : failures) {
				sb.append("- %s (%s): %s%n".formatted(f.displayName, f.speakerId, f.errorMessage));
			}
		}
		return sb.toString();
	}

	/** Details of a successfully generated banner. */
	public static final class SuccessEntry {

		public final UUID speakerId;

		public final String displayName;

		public final int bannerSize;

		SuccessEntry(UUID speakerId, String displayName, int bannerSize) {
			this.speakerId = speakerId;
			this.displayName = displayName;
			this.bannerSize = bannerSize;
		}

	}

	/** Details of a banner that failed to generate. */
	public static final class FailureEntry {

		public final UUID speakerId;

		public final String displayName;

		public final String errorMessage;

		FailureEntry(UUID speakerId, String displayName, String errorMessage) {
			this.speakerId = speakerId;
			this.displayName = displayName;
			this.errorMessage = errorMessage;
		}

	}

}
