package br.com.valorae.carteira.util;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyUtils {
    private static final Locale PT_BR = new Locale("pt", "BR");
    private MoneyUtils() {}

    public static String brl(double value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value);
    }

    public static String pct(double value) {
        return String.format(PT_BR, "%.2f%%", value);
    }

    public static String signedBrl(double value) {
        return (value > 0 ? "+" : value < 0 ? "-" : "") + brl(Math.abs(value));
    }

    public static String signedPct(double value) {
        return String.format(PT_BR, "%+.2f%%", value);
    }

    public static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) return String.format(PT_BR, "%.1f bi", value / 1_000_000_000d);
        if (abs >= 1_000_000) return String.format(PT_BR, "%.1f mi", value / 1_000_000d);
        if (abs >= 1_000) return String.format(PT_BR, "%.1f mil", value / 1_000d);
        return String.format(PT_BR, "%.0f", value);
    }
}
