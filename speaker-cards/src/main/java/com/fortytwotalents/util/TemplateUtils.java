package com.fortytwotalents.util;

import org.springframework.stereotype.Component;

/**
 * Template utility methods exposed to Thymeleaf templates via the {@code utils} variable.
 *
 * <p>Register as a Spring bean so {@link com.fortytwotalents.controller.BannerController}
 * can inject it into every template rendering context:
 * <pre>
 *   ctx.setVariable("utils", templateUtils);
 * </pre>
 *
 * <p>In templates use:
 * <pre>
 *   ${utils.formatDate(talk.date)}
 *   ${utils.formatTime(talk.cetTime)}
 *   ${utils.addHour(talk.cetTime)}
 *   ${utils.capitalise(speaker.title)}
 * </pre>
 */
@Component
public final class TemplateUtils {

    private TemplateUtils() {
        // utility class
    }

    /**
     * Capitalises the first letter of each whitespace-separated word in the
     * given string.
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
     * {@code HH:mm}.  For example {@code "11:00:00"} → {@code "11:00"}.
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
     * format.  The hour wraps around at 24.
     * For example {@code "14:00:00"} → {@code "15:00"}.
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
                return String.format("%02d:%02d", hour, minute);
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return formatTime(time);
    }

    /**
     * Formats a date string from the stored {@code yyyy-MM-dd} representation
     * into a human-readable form such as {@code "January, 22nd, 2026"}.
     *
     * @param date date string in {@code yyyy-MM-dd} format; may be {@code null}
     * @return formatted date string, or the original input if parsing fails
     */
    public static String formatDate(String date) {
        if (date == null || date.isEmpty()) {
            return "";
        }
        try {
            // Expected format: "yyyy-MM-dd"
            String[] parts = date.split("-");
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);

                String monthName = switch (month) {
                    case 1 -> "January";
                    case 2 -> "February";
                    case 3 -> "March";
                    case 4 -> "April";
                    case 5 -> "May";
                    case 6 -> "June";
                    case 7 -> "July";
                    case 8 -> "August";
                    case 9 -> "September";
                    case 10 -> "October";
                    case 11 -> "November";
                    case 12 -> "December";
                    default -> String.valueOf(month);
                };

                return monthName + ", " + day + getOrdinalSuffix(day) + ", " + year;
            }
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
}
