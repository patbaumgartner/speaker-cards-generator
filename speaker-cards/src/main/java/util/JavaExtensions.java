package util;

import io.quarkus.qute.TemplateExtension;

/**
 * Add your custom Qute extension methods here.
 */
@TemplateExtension
public class JavaExtensions {
    /**
     * This registers the String.capitalise extension method
     */
    public static String capitalise(String string) {
        StringBuilder sb = new StringBuilder();
        for (String part : string.split("\\s+")) {
            if(sb.length() > 0) {
                sb.append(" ");
            }
            if(part.length() > 0) {
            sb.append(part.substring(0, 1).toUpperCase());
            sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Formats time string by removing seconds (e.g., "11:00:00" -> "11:00")
     */
    @TemplateExtension(namespace = "str")
    public static String formatTime(String time) {
        if (time == null || time.isEmpty()) {
            return "";
        }
        if (time.length() >= 5) {
            return time.substring(0, 5);
        }
        return time;
    }

    /**
     * Adds 1 hour to a time string (e.g., "14:00:00" -> "15:00")
     * Assumes talks are 1 hour long
     */
    @TemplateExtension(namespace = "str")
    public static String addHour(String time) {
        if (time == null || time.isEmpty()) {
            return "";
        }
        try {
            // First format the time to remove seconds
            String formatted = formatTime(time);
            String[] parts = formatted.split(":");
            if (parts.length >= 2) {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                hour = (hour + 1) % 24; // Add 1 hour, wrap around at 24
                return String.format("%02d:%02d", hour, minute);
            }
        } catch (Exception e) {
            // If parsing fails, return formatted time
        }
        return formatTime(time);
    }

    /**
     * Formats date string from "Thursday, 22-Jan" to "January, 22nd, 2025"
     */
    @TemplateExtension(namespace = "str")
    public static String formatDate(String date) {
        if (date == null || date.isEmpty()) {
            return "";
        }
        try {
            // Parse "Thursday, 22-Jan" format
            String[] parts = date.split(", ");
            if (parts.length == 2) {
                String dayPart = parts[1]; // "22-Jan"
                String[] dayMonth = dayPart.split("-");
                if (dayMonth.length == 2) {
                    String day = dayMonth[0];
                    String monthAbbr = dayMonth[1];
                    
                    // Convert month abbreviation to full name
                    String month = switch (monthAbbr) {
                        case "Jan" -> "January";
                        case "Feb" -> "February";
                        case "Mar" -> "March";
                        case "Apr" -> "April";
                        case "May" -> "May";
                        case "Jun" -> "June";
                        case "Jul" -> "July";
                        case "Aug" -> "August";
                        case "Sep" -> "September";
                        case "Oct" -> "October";
                        case "Nov" -> "November";
                        case "Dec" -> "December";
                        default -> monthAbbr;
                    };
                    
                    // Add ordinal suffix
                    int dayNum = Integer.parseInt(day);
                    String suffix = getOrdinalSuffix(dayNum);
                    
                    return month + ", " + day + suffix + ", 2025";
                }
            }
        } catch (Exception e) {
            // If parsing fails, return original
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
