package com.fortytwotalents.speakercardsgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DevoxxImportServiceUrlTest {

	@Test
	void keepsHttpsUrlsAndDropsUnsafeSchemes() {
		assertThat(DevoxxImportService.safeHttpsUrl(" https://mastodon.social/@jane "))
			.isEqualTo("https://mastodon.social/@jane");
		assertThat(DevoxxImportService.safeHttpsUrl("javascript:alert(1)")).isNull();
		assertThat(DevoxxImportService.safeHttpsUrl("http://mastodon.social/@jane")).isNull();
		assertThat(DevoxxImportService.safeHttpsUrl("https://")).isNull();
		assertThat(DevoxxImportService.safeHttpsUrl("https://bad host/@jane")).isNull();
		assertThat(DevoxxImportService.safeHttpsUrl("  ")).isNull();
	}

}
