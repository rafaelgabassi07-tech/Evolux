package br.com.valorae.carteira.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateUtils {
    private static final Locale PT_BR = new Locale("pt", "BR");
    private DateUtils() {}

    public static String todayIso() { return formatIso(new Date()); }

    public static Date parseFlexible(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String[] patterns = new String[]{"yyyy-MM-dd", "yyyy-MM-dd\'T\'HH:mm:ss\'Z\'", "yyyy-MM-dd\'T\'HH:mm:ss.SSS\'Z\'", "dd/MM/yyyy", "yyyy/MM/dd", "dd-MM-yyyy"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(pattern, PT_BR);
                fmt.setLenient(false);
                return stripTime(fmt.parse(s));
            } catch (ParseException ignored) {}
        }
        return null;
    }

    public static String normalizeIsoDate(String raw, String fallback) {
        Date d = parseFlexible(raw);
        if (d != null) return formatIso(d);
        Date fb = parseFlexible(fallback);
        return fb == null ? todayIso() : formatIso(fb);
    }

    public static String formatIso(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(stripTime(date == null ? new Date() : date));
    }

    public static String formatBr(String isoOrDate) {
        Date d = parseFlexible(isoOrDate);
        if (d == null) return isoOrDate == null ? "" : isoOrDate;
        return new SimpleDateFormat("dd/MM/yyyy", PT_BR).format(d);
    }

    public static long daysBetween(String startIso, String endIso) {
        Date start = parseFlexible(startIso);
        Date end = parseFlexible(endIso);
        if (start == null || end == null) return 0;
        long diff = stripTime(end).getTime() - stripTime(start).getTime();
        return Math.max(0, diff / 86400000L);
    }

    public static long holdingDays(String purchaseDate) { return Math.max(1, daysBetween(purchaseDate, todayIso())); }
    public static double yearsFromDays(long days) { return days / 365.25d; }

    public static String humanDuration(long days) {
        if (days <= 0) return "Hoje";
        long years = days / 365;
        long months = (days % 365) / 30;
        if (years > 0 && months > 0) return years + "a " + months + "m";
        if (years > 0) return years + " ano" + (years > 1 ? "s" : "");
        if (months > 0) return months + " mês" + (months > 1 ? "es" : "");
        return days + " dia" + (days > 1 ? "s" : "");
    }

    private static Date stripTime(Date input) {
        Calendar c = Calendar.getInstance();
        c.setTime(input == null ? new Date() : input);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
