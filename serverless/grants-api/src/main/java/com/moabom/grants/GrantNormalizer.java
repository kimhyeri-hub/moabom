package com.moabom.grants;

import java.text.Normalizer;
import java.util.Locale;

/** Normalizes policy text for comparison. It does not invent missing eligibility values. */
public final class GrantNormalizer {
    private GrantNormalizer() {
    }

    public static String titleKey(String title) {
        String normalized = fold(title);
        if (normalized.startsWith("[예시]")) {
            normalized = normalized.substring("[예시]".length()).trim();
        }
        return normalized;
    }

    public static String regionKey(String region) {
        return fold(region);
    }

    public static String urlKey(String url) {
        String normalized = fold(url).toLowerCase(Locale.ROOT);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String dateKey(String value) {
        return fold(value);
    }

    public static boolean samePolicy(Grant left, Grant right) {
        if (left == null || right == null) {
            return false;
        }
        if (sameToken(left.sourceServiceId(), right.sourceServiceId())) {
            return true;
        }
        if (sameToken(urlKey(left.sourceUrl()), urlKey(right.sourceUrl()))) {
            return true;
        }
        return sameToken(titleKey(left.title()), titleKey(right.title()))
                && sameToken(regionKey(regionOf(left)), regionKey(regionOf(right)))
                && sameToken(dateKey(left.applicationStart()), dateKey(right.applicationStart()))
                && sameToken(dateKey(left.endDate()), dateKey(right.endDate()));
    }

    private static String regionOf(Grant grant) {
        if (grant.eligibility() == null) {
            return "";
        }
        return grant.eligibility().targetRegion();
    }

    private static boolean sameToken(String left, String right) {
        return left != null && !left.isEmpty() && left.equals(right);
    }

    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }
}
