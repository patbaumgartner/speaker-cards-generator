package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Session;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.SessionRepository;
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
 * Bulk generation of speaker, session and social banners.
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

	private final SessionRepository sessionRepository;

	private final BannerRenderer renderer;

	public BannerGenerationService(SpeakerRepository speakerRepository, SessionRepository sessionRepository,
			BannerRenderer renderer) {
		this.speakerRepository = speakerRepository;
		this.sessionRepository = sessionRepository;
		this.renderer = renderer;
	}

	/**
	 * Renders a speaker banner for every speaker/session pair, without writing to disk.
	 * @return per-banner successes and failures
	 */
	@Transactional(readOnly = true)
	public BannerGenerationResult generateAllSpeakerBanners() {
		List<Speaker> speakers = this.speakerRepository.findAllWithSessionsBy();
		log.info("Generating speaker banners for {} speakers", speakers.size());

		BannerGenerationResult result = new BannerGenerationResult();
		for (Speaker speaker : speakers) {
			generate(result, speaker, BannerType.SPEAKER, null);
		}
		return result;
	}

	/**
	 * Renders all session variants of speaker banners for the given speaker IDs only.
	 * @param speakerIds speaker UUIDs to process
	 * @return per-banner successes and failures
	 */
	@Transactional(readOnly = true)
	public BannerGenerationResult generateSpeakerBanners(List<UUID> speakerIds) {
		log.info("Generating speaker banners for {} selected speakers", speakerIds.size());
		BannerGenerationResult result = new BannerGenerationResult();
		for (UUID id : speakerIds) {
			this.speakerRepository.findWithSessionsById(id)
				.ifPresentOrElse(speaker -> generate(result, speaker, BannerType.SPEAKER, null),
						() -> result.addFailure(id, "Unknown", "Speaker not found"));
		}
		return result;
	}

	/**
	 * Renders every banner type and writes each file to the given output directory.
	 *
	 * <p>
	 * Files use the same names as individual downloads and ZIP entries, grouped under
	 * {@code speaker/}, {@code sessions/} and {@code social/}.
	 * @param outputDirectory directory the files are written to
	 * @return aggregated result including the saved file paths
	 */
	@Transactional(readOnly = true)
	public BannerGenerationResult generateAllBanners(String outputDirectory) {
		List<Speaker> speakers = this.speakerRepository.findAllWithSessionsBy();
		List<Session> sessions = this.sessionRepository.findAllWithSpeakersBy();
		log.info("Generating banners for {} speakers and {} sessions into {}", speakers.size(), sessions.size(),
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
			generate(result, speaker, BannerType.SPEAKER, root);
			generate(result, speaker, BannerType.SOCIAL, root);
		}
		for (Session session : sessions) {
			try {
				byte[] png = this.renderer.renderPng(BannerType.SESSION, null, session);
				Path file = root.resolve(this.renderer.fileName(BannerType.SESSION, null, session));
				Files.write(file, png);
				result.addSuccess(null, session.getTitle(), png.length);
				result.addSavedFile(file.toString());
			}
			catch (Exception ex) {
				result.addFailure(null, session.getTitle(), ex.getMessage());
				log.error("Failed session banner for '{}' ({})", session.getTitle(), session.getId(), ex);
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
				for (Speaker speaker : this.speakerRepository.findAllWithSessionsBy()) {
					for (Session session : BannerRenderer.bannerSessions(speaker)) {
						for (BannerType type : speakerTypes) {
							addEntry(zip, this.renderer.fileName(type, speaker, session),
									() -> this.renderer.renderPng(type, speaker, session),
									type + " banner for " + speaker.displayName()
											+ (session == null ? "" : " / " + session.getTitle()));
						}
					}
				}
			}
			if (types.contains(BannerType.SESSION)) {
				for (Session session : this.sessionRepository.findAllWithSpeakersBy()) {
					addEntry(zip, this.renderer.fileName(BannerType.SESSION, null, session),
							() -> this.renderer.renderPng(BannerType.SESSION, null, session),
							"session banner for '" + session.getTitle() + "'");
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

	/** Renders every session variant, optionally saving them under {@code outputRoot}. */
	private void generate(BannerGenerationResult result, Speaker speaker, BannerType type, Path outputRoot) {
		for (Session session : BannerRenderer.bannerSessions(speaker)) {
			String description = speaker.displayName() + (session == null ? "" : " / " + session.getTitle());
			try {
				byte[] png = this.renderer.renderPng(type, speaker, session);
				if (outputRoot != null) {
					Path file = outputRoot.resolve(this.renderer.fileName(type, speaker, session));
					Files.write(file, png);
					result.addSavedFile(file.toString());
				}
				result.addSuccess(speaker.getId(), description, png.length);
			}
			catch (Exception ex) {
				result.addFailure(speaker.getId(), description, ex.getMessage());
				log.error("Failed {} banner for {} ({})", type, description, speaker.getId(), ex);
			}
		}
	}

}
