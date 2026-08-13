package com.fortytwotalents.speakercardsgenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Runtime storage configuration, bound from the {@code app.storage.*} namespace.
 *
 * <p>
 * Speaker profile pictures are downloaded during import and must be written to a
 * directory that exists at <em>runtime</em>. Writing them back into
 * {@code src/main/resources} only ever worked when the application was started from an
 * exploded IDE build; from an executable JAR or a container image the files landed in a
 * throw-away directory that was never on the classpath, so imported photos silently never
 * appeared.
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageConfig {

	/**
	 * Directory for downloaded speaker profile pictures. Relative paths are resolved
	 * against the working directory. Mount this as a volume when running in a container
	 * so photos survive restarts.
	 */
	private String photoDir = "data/speaker-photos";

	/**
	 * Maximum accepted size of a downloaded profile picture. Downloads exceeding this
	 * limit are aborted, so a hostile or misconfigured image URL cannot fill the disk.
	 */
	private DataSize maxPhotoSize = DataSize.ofMegabytes(5);

	/**
	 * Whether profile pictures may be downloaded from loopback, link-local and private
	 * network addresses. Disabled by default: import data originates from third-party CFP
	 * systems, so an attacker-controlled image URL such as
	 * {@code http://169.254.169.254/latest/meta-data/} would otherwise turn the import
	 * into a server-side request forgery primitive. Enable only for local testing.
	 */
	private boolean allowPrivateNetworkPhotoUrls;

	public String getPhotoDir() {
		return photoDir;
	}

	public void setPhotoDir(String photoDir) {
		this.photoDir = photoDir;
	}

	public DataSize getMaxPhotoSize() {
		return maxPhotoSize;
	}

	public void setMaxPhotoSize(DataSize maxPhotoSize) {
		this.maxPhotoSize = maxPhotoSize;
	}

	public boolean isAllowPrivateNetworkPhotoUrls() {
		return allowPrivateNetworkPhotoUrls;
	}

	public void setAllowPrivateNetworkPhotoUrls(boolean allowPrivateNetworkPhotoUrls) {
		this.allowPrivateNetworkPhotoUrls = allowPrivateNetworkPhotoUrls;
	}

}
