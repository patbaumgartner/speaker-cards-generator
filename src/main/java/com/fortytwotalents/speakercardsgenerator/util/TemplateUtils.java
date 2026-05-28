package com.fortytwotalents.speakercardsgenerator.util;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Template utility methods exposed to Thymeleaf templates via the {@code utils}
 * variable.
 *
 * <p>
 * Register as a Spring bean so
 * {@link com.fortytwotalents.speakercardsgenerator.controller.BannerController}
 * can
 * inject it into every template rendering context:
 *
 * <pre>
 * ctx.setVariable("utils", templateUtils);
 * </pre>
 *
 * <p>
 * In templates use:
 *
 * <pre>
 *   ${utils.formatDate(talk.date)}
 *   ${utils.formatTime(talk.cetTime)}
 *   ${utils.addHour(talk.cetTime)}
 *   ${utils.capitalise(speaker.title)}
 * </pre>
 */
@Component
public final class TemplateUtils {

	private static final Map<Integer, Font> FONT_CACHE = new ConcurrentHashMap<>();

	/** Words that should not appear alone at the end of a line. */
	private static final Set<String> NON_BREAKING_WORDS = Set.of("a", "an", "the", "in", "on", "at", "to", "by", "of",
			"as", "or", "is", "vs", "vs.", "and", "for", "nor", "but", "yet", "so");

	/**
	 * Matches a token that consists entirely of emoji (with optional variation
	 * selectors
	 * / ZWJ).
	 */
	private static final Pattern EMOJI_ONLY = Pattern
			.compile("^[\\p{So}\\p{Sk}\\p{Cn}\\uFE0E\\uFE0F\\u200D\\u20E3" + "\\x{1F3FB}-\\x{1F3FF}" + "]+$");

	/**
	 * Capitalises the first letter of each whitespace-separated word in the given
	 * string.
	 * 
	 * @param string input string; may be {@code null}
	 * @return capitalised string, or an empty string if the input is {@code null}
	 */
	public static String capitalise(String string) {
		if (string == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (String part : string.split("\\s+")) {
			if (!sb.isEmpty()) {
				sb.append(" ");
			}
			if (!part.isEmpty()) {
				sb.append(Character.toUpperCase(part.charAt(0)));
				sb.append(part.substring(1));
			}
		}
		return sb.toString();
	}

	/**
	 * Removes the seconds component from a time string, returning only
	 * {@code HH:mm}. For
	 * example {@code "11:00:00"} → {@code "11:00"}.
	 * 
	 * @param time time string; may be {@code null} or empty
	 * @return formatted time string, or an empty string if the input is blank
	 */
	public static String formatTime(String time) {
		if (time == null || time.isEmpty()) {
			return "";
		}
		return time.length() >= 5 ? time.substring(0, 5) : time;
	}

	/**
	 * Adds one hour to a time string and returns the result in {@code HH:mm}
	 * format. The
	 * hour wraps around at 24. For example {@code "14:00:00"} → {@code "15:00"}.
	 * 
	 * @param time time string; may be {@code null} or empty
	 * @return time plus one hour, or the formatted input if parsing fails
	 */
	public static String addHour(String time) {
		if (time == null || time.isEmpty()) {
			return "";
		}
		try {
			String formatted = formatTime(time);
			String[] parts = formatted.split(":");
			if (parts.length >= 2) {
				int hour = Integer.parseInt(parts[0]);
				int minute = Integer.parseInt(parts[1]);
				hour = (hour + 1) % 24;
				return "%02d:%02d".formatted(hour, minute);
			}
		} catch (Exception ignored) {
			// fall through to default
		}
		return formatTime(time);
	}

	/**
	 * Formats a date string from the stored {@code yyyy-MM-dd} representation into
	 * a
	 * human-readable form such as {@code "January, 22nd, 2026"}.
	 * 
	 * @param date date string in {@code yyyy-MM-dd} format; may be {@code null}
	 * @return formatted date string, or the original input if parsing fails
	 */
	public static String formatDate(String date) {
		if (date == null || date.isEmpty()) {
			return "";
		}
		try {
			LocalDate ld = LocalDate.parse(date);
			int day = ld.getDayOfMonth();
			String monthName = ld.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
			return monthName + ", " + day + getOrdinalSuffix(day) + ", " + ld.getYear();
		} catch (Exception ignored) {
			// fall through to default
		}
		return date;
	}

	private static String getOrdinalSuffix(int day) {
		if (day >= 11 && day <= 13) {
			return "th";
		}
		return switch (day % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}

	/**
	 * Converts a CSS hex colour (e.g. {@code "#1e2246"}) and alpha value to an
	 * {@code rgba(r, g, b, a)} CSS string.
	 * 
	 * @param hex   hex colour string with leading {@code #}
	 * @param alpha opacity value between 0.0 and 1.0
	 * @return rgba CSS value, e.g. {@code "rgba(30, 34, 70, 0.85)"}
	 */
	public static String rgba(String hex, double alpha) {
		if (hex == null || hex.length() < 7) {
			return "rgba(0, 0, 0, " + alpha + ")";
		}
		String clean = hex.startsWith("#") ? hex.substring(1) : hex;
		int r = Integer.parseInt(clean.substring(0, 2), 16);
		int g = Integer.parseInt(clean.substring(2, 4), 16);
		int b = Integer.parseInt(clean.substring(4, 6), 16);
		return "rgba(" + r + ", " + g + ", " + b + ", " + alpha + ")";
	}

	/**
	 * Sanitises a user-supplied formatted title by stripping every HTML tag except
	 * {@code <br>
	 * }. This prevents XSS while preserving the manual line-breaks the user entered
	 * in
	 * the title editor.
	 * 
	 * @param html raw input from the editor; may be {@code null}
	 * @return sanitised string containing only plain text and {@code <br>} tags
	 */
	public static String sanitizeFormattedTitle(String html) {
		if (html == null || html.isEmpty()) {
			return null;
		}
		// Strip all tags except <br> variants, then normalise to <br/>
		// Use possessive quantifier <[^>]++> to prevent ReDoS backtracking
		String sanitized = html.replaceAll("(?i)<br\\s*/?>", "\n")
				.replaceAll("<[^>]++>", "")
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("\n", "<br/>");
		// If only whitespace remains after stripping, treat as empty
		if (sanitized.replace("<br/>", "").isBlank()) {
			return null;
		}
		return sanitized;
	}

	/**
	 * Formats a talk title for banner rendering with typographically correct line
	 * breaks.
	 * Uses the actual Poppins font metrics to measure text width and applies
	 * minimum-raggedness line breaking with orphan avoidance and clause-separator
	 * preference.
	 *
	 * <p>
	 * The result contains {@code <br>
	 * } tags and HTML-escaped text, suitable for use with {@code th:utext}. Callers
	 * pass
	 * the layout constants from the template so the algorithm adapts automatically
	 * when
	 * the design changes.
	 * 
	 * @param title            the talk title; may be {@code null}
	 * @param containerWidthPx available width in pixels for the title
	 * @param fontSizePx       CSS font-size in pixels
	 * @param fontWeight       CSS font-weight (300, 400, 600, or 700)
	 * @return HTML string with {@code <br>} at optimal break points
	 */
	public static String formatTitle(String title, int containerWidthPx, int fontSizePx, int fontWeight) {
		if (title == null || title.isBlank()) {
			return "";
		}
		String[] words = title.trim().split("\\s+");
		if (words.length == 0) {
			return "";
		}

		List<String> units = buildBreakUnits(words);
		int n = units.size();
		if (n == 0) {
			return escapeHtml(title);
		}

		Font font = resolveFont(fontWeight, fontSizePx);
		FontMetrics fm = createFontMetrics(font);
		int spaceWidth = fm.stringWidth(" ");

		int[] widths = new int[n];
		int totalWidth = 0;
		for (int i = 0; i < n; i++) {
			widths[i] = fm.stringWidth(units.get(i)) + estimateEmojiWidth(units.get(i), fontSizePx);
			if (i > 0) {
				totalWidth += spaceWidth;
			}
			totalWidth += widths[i];
		}

		// Fits on one line — no breaks needed
		if (totalWidth <= containerWidthPx) {
			return protectEmojiSpaces(escapeHtml(title));
		}

		int[] lineStarts = computeOptimalBreaks(units, widths, spaceWidth, containerWidthPx);
		return protectEmojiSpaces(buildFormattedHtml(units, lineStarts));
	}

	/**
	 * Groups words into break units that should not be split across lines.
	 * Standalone
	 * dashes attach to the preceding word; articles and short prepositions attach
	 * to the
	 * following word.
	 */
	static List<String> buildBreakUnits(String[] words) {
		// Pass 1: attach standalone separator tokens to the preceding word
		List<String> merged = new ArrayList<>();
		for (String word : words) {
			if (!merged.isEmpty() && isStandaloneSeparator(word)) {
				merged.set(merged.size() - 1, merged.getLast() + " " + word);
			} else {
				merged.add(word);
			}
		}

		// Pass 2: attach non-breaking words to the following word
		List<String> units = new ArrayList<>();
		int i = 0;
		while (i < merged.size()) {
			String current = merged.get(i);
			String trailing = extractTrailingWord(current);
			if (i < merged.size() - 1 && NON_BREAKING_WORDS.contains(trailing.toLowerCase(Locale.ROOT))) {
				units.add(current + " " + merged.get(i + 1));
				i += 2;
			} else {
				units.add(current);
				i++;
			}
		}

		// Pass 3: attach trailing emoji-only unit to the preceding unit
		if (units.size() > 1 && isEmojiOnly(units.getLast())) {
			String emoji = units.removeLast();
			units.set(units.size() - 1, units.getLast() + " " + emoji);
		}

		return units;
	}

	private static boolean isStandaloneSeparator(String word) {
		return "\u2013".equals(word) || "\u2014".equals(word) || ":".equals(word) || ";".equals(word);
	}

	private static boolean isEmojiOnly(String text) {
		return text != null && !text.isEmpty() && EMOJI_ONLY.matcher(text).matches();
	}

	private static String extractTrailingWord(String text) {
		int lastSpace = text.lastIndexOf(' ');
		return lastSpace >= 0 ? text.substring(lastSpace + 1) : text;
	}

	/**
	 * Computes optimal line-break positions using minimum-raggedness dynamic
	 * programming.
	 * Returns an array of indices into the units list marking the start of each
	 * line.
	 */
	private static int[] computeOptimalBreaks(List<String> units, int[] widths, int spaceWidth, int maxWidth) {
		int n = units.size();
		long[] cost = new long[n + 1];
		int[] next = new int[n];
		Arrays.fill(cost, Long.MAX_VALUE / 2);
		cost[n] = 0;

		for (int i = n - 1; i >= 0; i--) {
			int lineWidth = 0;
			for (int j = i; j < n; j++) {
				if (j > i) {
					lineWidth += spaceWidth;
				}
				lineWidth += widths[j];
				// Allow a single unit to overflow — CSS word-wrap handles it
				if (lineWidth > maxWidth && j > i) {
					break;
				}

				long lineCost;
				boolean isLastLine = (j == n - 1);

				if (isLastLine && lineWidth <= maxWidth) {
					// Last line: no penalty unless it's an orphan (< 1/3 width)
					if (lineWidth < maxWidth / 3 && i > 0) {
						long gap = maxWidth - lineWidth;
						lineCost = gap * gap * 2;
					} else {
						lineCost = 0;
					}
				} else if (lineWidth > maxWidth) {
					lineCost = Long.MAX_VALUE / 4;
				} else {
					long gap = maxWidth - lineWidth;
					lineCost = gap * gap;
					// Prefer breaking after clause separators (dashes, colons, commas)
					if (endsWithClauseSeparator(units.get(j))) {
						lineCost = Math.max(0, lineCost - (long) maxWidth * maxWidth / 4);
					}
				}

				long totalCost = lineCost + cost[j + 1];
				if (totalCost < cost[i]) {
					cost[i] = totalCost;
					next[i] = j + 1;
				}
			}
		}

		List<Integer> starts = new ArrayList<>();
		int pos = 0;
		while (pos < n) {
			starts.add(pos);
			pos = next[pos];
		}
		return starts.stream().mapToInt(Integer::intValue).toArray();
	}

	private static boolean endsWithClauseSeparator(String text) {
		if (text.isEmpty()) {
			return false;
		}
		char last = text.charAt(text.length() - 1);
		return last == '\u2013' || last == '\u2014' || last == ':' || last == ';' || last == ',';
	}

	private static String buildFormattedHtml(List<String> units, int[] lineStarts) {
		StringBuilder sb = new StringBuilder();
		for (int l = 0; l < lineStarts.length; l++) {
			if (l > 0) {
				sb.append("<br/>");
			}
			int start = lineStarts[l];
			int end = (l + 1 < lineStarts.length) ? lineStarts[l + 1] : units.size();
			for (int i = start; i < end; i++) {
				if (i > start) {
					sb.append(" ");
				}
				sb.append(escapeHtml(units.get(i)));
			}
		}
		return sb.toString();
	}

	private static String escapeHtml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/**
	 * Estimates the extra pixel width contributed by emoji characters that the
	 * Poppins
	 * font cannot measure (it has no emoji glyphs). Emoji are typically rendered as
	 * square glyphs at approximately the current font size.
	 */
	private static int estimateEmojiWidth(String text, int fontSizePx) {
		int count = 0;
		for (int i = 0; i < text.length();) {
			int cp = text.codePointAt(i);
			if (isEmojiCodePoint(cp)) {
				count++;
			}
			i += Character.charCount(cp);
		}
		// Each emoji glyph is roughly 1.2× the font size in width
		return count * (int) (fontSizePx * 1.2);
	}

	private static boolean isEmojiCodePoint(int cp) {
		int type = Character.getType(cp);
		return type == Character.OTHER_SYMBOL || type == Character.MODIFIER_SYMBOL || (cp >= 0x1F600 && cp <= 0x1F64F)
				|| (cp >= 0x1F300 && cp <= 0x1F5FF) || (cp >= 0x1F680 && cp <= 0x1F6FF)
				|| (cp >= 0x1F900 && cp <= 0x1F9FF) || (cp >= 0x2600 && cp <= 0x26FF) || (cp >= 0x2700 && cp <= 0x27BF);
	}

	/**
	 * Replaces regular spaces immediately before emoji characters with non-breaking
	 * spaces ({@code &nbsp;}) so the HTML/PDF renderer cannot break the line there.
	 */
	private static String protectEmojiSpaces(String html) {
		StringBuilder sb = new StringBuilder(html.length());
		for (int i = 0; i < html.length();) {
			int cp = html.codePointAt(i);
			int len = Character.charCount(cp);
			if (cp == ' ' && i + len < html.length() && isEmojiCodePoint(html.codePointAt(i + len))) {
				sb.append("&nbsp;");
			} else {
				sb.appendCodePoint(cp);
			}
			i += len;
		}
		return sb.toString();
	}

	private static Font resolveFont(int weight, int sizePx) {
		Font base = FONT_CACHE.computeIfAbsent(weight, w -> {
			String filename = switch (w) {
				case 300 -> "Poppins-Light.ttf";
				case 600 -> "Poppins-SemiBold.ttf";
				case 700 -> "Poppins-Bold.ttf";
				default -> "Poppins-Regular.ttf";
			};
			try (InputStream is = TemplateUtils.class.getResourceAsStream("/static/fonts/" + filename)) {
				if (is == null) {
					return new Font(Font.SANS_SERIF, Font.PLAIN, 1);
				}
				return Font.createFont(Font.TRUETYPE_FONT, is);
			} catch (Exception ex) {
				return new Font(Font.SANS_SERIF, Font.PLAIN, 1);
			}
		});
		return base.deriveFont((float) sizePx);
	}

	private static FontMetrics createFontMetrics(Font font) {
		BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		FontMetrics fm = g.getFontMetrics(font);
		g.dispose();
		return fm;
	}

	private TemplateUtils() {
	}

}
