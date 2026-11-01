package com.hypaflippa;

import java.util.Locale;

/** Number formatting shared by every window, so all tabs read the same. */
public final class Format {
    private Format() {}

    /** Short coin amounts: 950, 12.3k, 4.56M, 1.2B. Keeps a sign for negatives. */
    public static String coins(double value) {
        double abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";
        if (abs >= 1e9) return sign + trim(abs / 1e9, abs >= 1e10 ? 1 : 2) + "B";
        if (abs >= 1e6) return sign + trim(abs / 1e6, abs >= 1e7 ? 1 : 2) + "M";
        if (abs >= 1e4) return sign + trim(abs / 1e3, 1) + "k";
        return sign + String.format(Locale.ROOT, "%,.0f", abs);
    }

    /** Like {@link #coins} but always with a sign: "+12.3k", "-4.5M". */
    public static String signedCoins(double value) {
        return (value >= 0 ? "+" : "") + coins(value);
    }

    /** Full coin amount with separators: 1,234,567.8 (for prices where precision matters). */
    public static String exactCoins(double value) {
        return String.format(Locale.ROOT, "%,.1f", value);
    }

    /** 0.4405 -> "44.05%", 0.000125 -> "0.0125%". */
    public static String chance(double fraction) {
        double percent = fraction * 100;
        if (percent >= 10) return String.format(Locale.ROOT, "%.1f%%", percent);
        if (percent >= 0.1) return String.format(Locale.ROOT, "%.2f%%", percent);
        return String.format(Locale.ROOT, "%.4f%%", percent);
    }

    public static String percent(double percent) {
        return String.format(Locale.ROOT, "%+.1f%%", percent);
    }

    private static String trim(double value, int decimals) {
        String text = String.format(Locale.ROOT, "%." + decimals + "f", value);
        if (text.contains(".")) text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text;
    }
}
