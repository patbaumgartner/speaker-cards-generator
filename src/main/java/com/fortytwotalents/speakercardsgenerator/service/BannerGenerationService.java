package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer.BannerType;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk generation of speaker, talk and social banners.
 *
 * <p>
 * Banners are rendered in-process through {@link BannerRenderer}. A single failing record
 * never aborts a run: failures are collected into the returned
 * {@link BannerGenerationResult}, or logged and skipped for ZIP downloads, so a
 * conference with one malformed speaker still gets the other ninety-nine cards.
 */
@Service
public class BannerGenerationService {

	private static final Logger log = LoggerFactory.getLogger(BannerGenerationService.class);

	private final SpeakerRepository speakerRepository;

	private final TalkRepository talkRepository;

	private final BannerRenderer renderer;

	public BannerGenerationService(SpeakerRepository speakerRepository, TalkRepository talkRepository,
			BannerRenderer renderer) {
		this.speakerRepository = speakerRepository;
		this.talkRepository = talkRepository;
		this.renderer = renderer;
	}

	/**
	 * Renders a speaker banner for every speaker, without writing anything to disk.
	 * @return per-speaker successes and failures
	 */
	@Transactional(readOnly = true)
	public BannerGenerationResult generateAllSpeakerBanners() {
		List<Speaker> speakers = this.speakerRepository.findAllWithTalksBy();
		log.info("Generating speaker banners for {} speakers", speakers.size());

		BannerGenerationResult result = new BannerGenerationResult();
		for (Speaker speaker : speakers) {
			generate(result, speaker, BannerType.SPEAKER, null);
		}
		return result;
	}

	/**
	 * Renders speaker banners for the given speaker IDs only.
	 * @param speakerIds speaker UUIDs to process
	 * @return per-speaker successes and failures
	 */
	@Transactional(readOnly = true)
	public BannerGenerationResult generateSpeakerBanners(List<UUID> speakerIds) {
		log.info("Generating speaker banners for {} selected speakers", speakerIds.size());
		BannerGenerationResult result = new BannerGenerationResult();
		for (UUID id : speakerIds) {
			this.speakerRepository.findWithTalksById(id)
				.ifPresentOrElse(speaker -> generate(result, speaker, BannerType.SPEAKER, null),
						() -> result.addFailure(id, "Unknown", "Speaker not found"));
		}
		return result;
	}

	/**
	 * Renders every banner type and writes each file to the given output directory.
	 *
	 * <p>
	 * Output layout:
	 *
	 * <pre>
	 * {outputDirectory}/
	 *   speaker/   ← {speakerId}.png
	 *   talks/     ← {talkId}.png
	 *   social/    ← {lastName}_{firstName}.png
	 * </pre>
	 * @param outputDirectory directory the files are written to
	 * @return aggregated result including the saved file paths
	 */
	@Transactional(readOnly = true)
	public BannerGenerationResult generateAllBanners(String outputDirectory) {
		List<Speaker> speakers = this.speakerRepository.findAllWithTalksBy();
		List<Talk> talks = this.talkRepository.findAllWithSpeakersBy();
		log.info("Generating banners for {} speakers and {} talks into {}", speakers.size(), talks.size(),
				outputDirectory);

		Path root = Path.of(outputDirectory);
		try {
			for (BannerType type : BannerType.values()) {
				Files.createDirectories(root.resolve(type.directory()));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to create output directories under " + outputDirectory, ex);
		}

		BannerGenerationResult result = new BannerGenerationResult();
		for (Speaker speaker : speakers) {
			generate(result, speaker, BannerType.SPEAKER,
					root.resolve(BannerType.SPEAKER.directory()).resolve(speaker.getId() + ".png"));
			generate(result, speaker, BannerType.SOCIAL,
					root.resolve(BannerRenderer.fileName(BannerType.SOCIAL, speaker)));
		}
		for (Talk talk : talks) {
			try {
				byte[] png = this.renderer.renderPng(BannerType.TALK, null, talk);
				Path file = root.resolve(BannerRenderer.fileName(talk));
				Files.write(file, png);
				result.addSuccess(null, talk.getTitle(), png.length);
				result.addSavedFile(file.toString());
			}
			catch (Exception ex) {
				result.addFailure(null, talk.getTitle(), ex.getMessage());
				log.error("Failed talk banner for '{}' ({})", talk.getTitle(), talk.getId(), ex);
			}
		}

		log.info("Banner generation completed: {} successful, {} failed", result.getSuccessCount(),
				result.getFailureCount());
		return result;
	}

	/**
	 * Streams the requested banner types into a ZIP archive.
	 * @param out stream the archive is written to; closed by this method
	 * @param types banner types to include
	 * @throws IOException if the archive cannot be written
	 */
	@Transactional(readOnly = true)
	public void writeZip(OutputStream out, Set<BannerType> types) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			List<BannerType> speakerTypes = List.of(BannerType.SPEAKER, BannerType.SOCIAL)
				.stream()
				.filter(types::contains)
				.toList();
			if (!speakerTypes.isEmpty()) {
				for (Speaker speaker : this.speakerRepository.findAllWithTalksBy()) {
					for (BannerType type : speakerTypes) {
						addEntry(zip, BannerRenderer.fileName(type, speaker),
								() -> this.renderer.renderSpeakerPng(type, speaker),
								type + " banner for " + speaker.displayName());
					}
				}
			}
			if (types.contains(BannerType.TALK)) {
				for (Talk talk : this.talkRepository.findAllWithSpeakersBy()) {
					addEntry(zip, BannerRenderer.fileName(talk),
							() -> this.renderer.renderPng(BannerType.TALK, null, talk),
							"talk banner for '" + talk.getTitle() + "'");
				}
			}
		}
	}

	private void addEntry(ZipOutputStream zip, String name, Supplier<byte[]> png, String description)
			throws IOException {
		byte[] content;
		try {
			content = png.get();
		}
		catch (Exception ex) {
			log.error("Skipping {} — rendering failed", description, ex);
			return;
		}
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content);
		zip.closeEntry();
	}

	/** Renders one speaker banner, optionally saving it to {@code file}. */
	private void generate(BannerGenerationResult result, Speaker speaker, BannerType type, Path file) {
		try {
			byte[] png = this.renderer.renderSpeakerPng(type, speaker);
			if (file != null) {
				Files.write(file, png);
				result.addSavedFile(file.toString());
			}
			result.addSuccess(speaker.getId(), speaker.displayName(), png.length);
		}
		catch (Exception ex) {
			result.addFailure(speaker.getId(), speaker.displayName(), ex.getMessage());
			log.error("Failed {} banner for {} ({})", type, speaker.displayName(), speaker.getId(), ex);
		}
	}

}
