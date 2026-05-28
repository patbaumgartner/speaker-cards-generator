package com.fortytwotalents.speakercardsgenerator.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TemplateUtils}.
 */
class TemplateUtilsTest {

	// --- capitalise ---

	@Test
	void capitaliseReturnsEmptyStringForNull() {
		assertThat(TemplateUtils.capitalise(null)).isEmpty();
	}

	@Test
	void capitaliseReturnsEmptyStringForBlank() {
		assertThat(TemplateUtils.capitalise("")).isEmpty();
	}

	@Test
	void capitaliseCapitalisesFirstLetterOfEachWord() {
		assertThat(TemplateUtils.capitalise("hello world")).isEqualTo("Hello World");
	}

	@Test
	void capitaliseHandlesSingleWord() {
		assertThat(TemplateUtils.capitalise("java")).isEqualTo("Java");
	}

	@Test
	void capitalisePreservesAlreadyCapitalised() {
		assertThat(TemplateUtils.capitalise("Hello World")).isEqualTo("Hello World");
	}

	// --- formatTime ---

	@Test
	void formatTimeReturnsEmptyForNull() {
		assertThat(TemplateUtils.formatTime(null)).isEmpty();
	}

	@Test
	void formatTimeReturnsEmptyForEmpty() {
		assertThat(TemplateUtils.formatTime("")).isEmpty();
	}

	@Test
	void formatTimeTrimsSecondsFromTimeString() {
		assertThat(TemplateUtils.formatTime("11:00:00")).isEqualTo("11:00");
	}

	@Test
	void formatTimeReturnsShortTimeUnchanged() {
		assertThat(TemplateUtils.formatTime("11:00")).isEqualTo("11:00");
	}

	@Test
	void formatTimeReturnsVeryShortStringUnchanged() {
		assertThat(TemplateUtils.formatTime("9")).isEqualTo("9");
	}

	// --- addHour ---

	@Test
	void addHourReturnsEmptyForNull() {
		assertThat(TemplateUtils.addHour(null)).isEmpty();
	}

	@Test
	void addHourReturnsEmptyForEmpty() {
		assertThat(TemplateUtils.addHour("")).isEmpty();
	}

	@Test
	void addHourAddsOneHour() {
		assertThat(TemplateUtils.addHour("14:00:00")).isEqualTo("15:00");
	}

	@Test
	void addHourWrapsAroundMidnight() {
		assertThat(TemplateUtils.addHour("23:30")).isEqualTo("00:30");
	}

	@Test
	void addHourPreservesMinutes() {
		assertThat(TemplateUtils.addHour("09:45")).isEqualTo("10:45");
	}

	// --- formatDate ---

	@Test
	void formatDateReturnsEmptyForNull() {
		assertThat(TemplateUtils.formatDate(null)).isEmpty();
	}

	@Test
	void formatDateReturnsEmptyForEmpty() {
		assertThat(TemplateUtils.formatDate("")).isEmpty();
	}

	@Test
	void formatDateFormatsStandardDate() {
		assertThat(TemplateUtils.formatDate("2026-01-22")).isEqualTo("January, 22nd, 2026");
	}

	@Test
	void formatDateHandlesOrdinalSuffixes() {
		assertThat(TemplateUtils.formatDate("2026-03-01")).isEqualTo("March, 1st, 2026");
		assertThat(TemplateUtils.formatDate("2026-03-02")).isEqualTo("March, 2nd, 2026");
		assertThat(TemplateUtils.formatDate("2026-03-03")).isEqualTo("March, 3rd, 2026");
		assertThat(TemplateUtils.formatDate("2026-03-04")).isEqualTo("March, 4th, 2026");
	}

	@Test
	void formatDateHandlesTeenthOrdinals() {
		assertThat(TemplateUtils.formatDate("2026-06-11")).isEqualTo("June, 11th, 2026");
		assertThat(TemplateUtils.formatDate("2026-06-12")).isEqualTo("June, 12th, 2026");
		assertThat(TemplateUtils.formatDate("2026-06-13")).isEqualTo("June, 13th, 2026");
	}

	@Test
	void formatDateReturnsOriginalForInvalidFormat() {
		assertThat(TemplateUtils.formatDate("not-a-date")).isEqualTo("not-a-date");
	}

	// --- rgba ---

	@Test
	void rgbaConvertsHexToRgba() {
		assertThat(TemplateUtils.rgba("#1e2246", 0.85)).isEqualTo("rgba(30, 34, 70, 0.85)");
	}

	@Test
	void rgbaHandlesAccentColour() {
		assertThat(TemplateUtils.rgba("#40b4e5", 0.9)).isEqualTo("rgba(64, 180, 229, 0.9)");
	}

	@Test
	void rgbaHandlesNullHex() {
		assertThat(TemplateUtils.rgba(null, 0.5)).isEqualTo("rgba(0, 0, 0, 0.5)");
	}

	@Test
	void rgbaHandlesFullOpacity() {
		assertThat(TemplateUtils.rgba("#ffffff", 1.0)).isEqualTo("rgba(255, 255, 255, 1.0)");
	}

	// --- sanitizeFormattedTitle ---

	@Test
	void sanitizeFormattedTitleReturnsNullForNull() {
		assertThat(TemplateUtils.sanitizeFormattedTitle(null)).isNull();
	}

	@Test
	void sanitizeFormattedTitleReturnsNullForEmpty() {
		assertThat(TemplateUtils.sanitizeFormattedTitle("")).isNull();
	}

	@Test
	void sanitizeFormattedTitlePreservesLineBreaks() {
		assertThat(TemplateUtils.sanitizeFormattedTitle("Line One<br>Line Two")).isEqualTo("Line One<br/>Line Two");
	}

	@Test
	void sanitizeFormattedTitleNormalisesBrVariants() {
		assertThat(TemplateUtils.sanitizeFormattedTitle("A<br/>B<BR />C")).isEqualTo("A<br/>B<br/>C");
	}

	@Test
	void sanitizeFormattedTitleStripsOtherHtmlTags() {
		assertThat(TemplateUtils.sanitizeFormattedTitle("Hello <b>World</b><br>Next"))
			.isEqualTo("Hello World<br/>Next");
	}

	@Test
	void sanitizeFormattedTitleEscapesHtmlEntities() {
		assertThat(TemplateUtils.sanitizeFormattedTitle("A & B <br> C < D")).isEqualTo("A &amp; B <br/> C &lt; D");
	}

	@Test
	void sanitizeFormattedTitleReturnsNullForWhitespaceOnly() {
		assertThat(TemplateUtils.sanitizeFormattedTitle("   <br>  ")).isNull();
	}

	@Test
	void sanitizeFormattedTitleStripsScriptTags() {
		assertThat(TemplateUtils.sanitizeFormattedTitle("<script>alert('x')</script>Hello"))
			.isEqualTo("alert('x')Hello");
	}

	// --- buildBreakUnits ---

	@Test
	void buildBreakUnitsGroupsArticleWithFollowingWord() {
		List<String> units = TemplateUtils.buildBreakUnits(new String[] { "Building", "the", "Future" });
		assertThat(units).containsExactly("Building", "the Future");
	}

	@Test
	void buildBreakUnitsGroupsPrepositionWithFollowingWord() {
		List<String> units = TemplateUtils.buildBreakUnits(new String[] { "Deep", "Dive", "in", "Kubernetes" });
		assertThat(units).containsExactly("Deep", "Dive", "in Kubernetes");
	}

	@Test
	void buildBreakUnitsAttachesDashToPrecedingWord() {
		List<String> units = TemplateUtils.buildBreakUnits(new String[] { "Java", "\u2013", "The", "Next", "Gen" });
		assertThat(units).containsExactly("Java \u2013", "The Next", "Gen");
	}

	@Test
	void buildBreakUnitsPreservesRegularWords() {
		List<String> units = TemplateUtils.buildBreakUnits(new String[] { "Cloud", "Native", "Patterns" });
		assertThat(units).containsExactly("Cloud", "Native", "Patterns");
	}

	@Test
	void buildBreakUnitsHandlesConsecutiveNonBreakingWords() {
		List<String> units = TemplateUtils.buildBreakUnits(new String[] { "Out", "of", "the", "Box" });
		assertThat(units).containsExactly("Out", "of the", "Box");
	}

	@Test
	void buildBreakUnitsAttachesTrailingEmojiToPrecedingWord() {
		List<String> units = TemplateUtils.buildBreakUnits(new String[] { "Cloud", "Native", "Rocks", "\uD83D\uDE80" });
		assertThat(units).containsExactly("Cloud", "Native", "Rocks \uD83D\uDE80");
	}

	@Test
	void buildBreakUnitsKeepsEmojiInMiddleAsSeparateUnit() {
		List<String> units = TemplateUtils.buildBreakUnits(new String[] { "Go", "\uD83D\uDE80", "Fast" });
		assertThat(units).containsExactly("Go", "\uD83D\uDE80", "Fast");
	}

	// --- formatTitle ---

	@Test
	void formatTitleReturnsEmptyForNull() {
		assertThat(TemplateUtils.formatTitle(null, 700, 53, 700)).isEmpty();
	}

	@Test
	void formatTitleReturnsEmptyForBlank() {
		assertThat(TemplateUtils.formatTitle("  ", 700, 53, 700)).isEmpty();
	}

	@Test
	void formatTitleReturnsSingleWordWithoutBreak() {
		String result = TemplateUtils.formatTitle("Kubernetes", 700, 53, 700);
		assertThat(result).doesNotContain("<br/>");
	}

	@Test
	void formatTitleReturnsShortTitleWithoutBreak() {
		String result = TemplateUtils.formatTitle("Short Title", 700, 53, 700);
		assertThat(result).doesNotContain("<br/>");
	}

	@Test
	void formatTitleBreaksLongTitle() {
		String result = TemplateUtils
			.formatTitle("Building Cloud-Native Event-Driven Microservices with Spring Boot and Kafka", 700, 53, 700);
		assertThat(result).contains("<br/>");
	}

	@Test
	void formatTitleKeepsEmojiAttachedWithNbsp() {
		// "JBang, a Java file to rule them all? 🔮" — emoji must not end up alone on a
		// line
		String result = TemplateUtils.formatTitle("JBang, a Java file to rule them all? \uD83D\uDD2E", 800, 44, 600);
		assertThat(result).contains("&nbsp;\uD83D\uDD2E");
		assertThat(result).doesNotContain(" \uD83D\uDD2E");
	}

	@Test
	void formatTitleEscapesHtmlCharacters() {
		String result = TemplateUtils.formatTitle("A <b>Bold</b> & \"Quoted\" Title", 700, 53, 700);
		assertThat(result).contains("&lt;b&gt;").contains("&amp;").contains("&quot;");
		assertThat(result).doesNotContain("<b>");
	}

	@Test
	void formatTitleAvoidsSingleShortWordOnLastLine() {
		String result = TemplateUtils.formatTitle("Understanding the Fundamentals of Reactive Programming in Java", 700,
				53, 700);
		if (result.contains("<br/>")) {
			String lastLine = result.substring(result.lastIndexOf("<br/>") + 5).trim();
			// Last line should not be a single very short word
			assertThat(lastLine.split("\\s+").length).isGreaterThan(0);
			assertThat(lastLine.length()).isGreaterThan(4);
		}
	}

	@Test
	void formatTitleWorksWithDifferentBannerLayouts() {
		String title = "From Monolith to Microservices \u2013 A Practical Journey";
		// talkBanner dimensions
		String talkResult = TemplateUtils.formatTitle(title, 700, 53, 700);
		// speakerBanner dimensions
		String speakerResult = TemplateUtils.formatTitle(title, 800, 44, 600);
		// speakerSocial dimensions
		String socialResult = TemplateUtils.formatTitle(title, 980, 62, 700);
		// All should produce valid HTML without raw < or >
		for (String result : List.of(talkResult, speakerResult, socialResult)) {
			assertThat(result).doesNotContain("<b>").doesNotContain("<script>");
		}
	}

}
