package com.moabom.grants;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Recommendation labels chosen from policy text. They are not official eligibility fields.
 */
public final class InterestLabels {
    public static final Set<String> ALLOWED = Set.of("주거", "취업", "교육", "자산형성");

    private InterestLabels() {
    }

    public static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        TreeSet<String> unique = new TreeSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String label = value.trim();
            if (!ALLOWED.contains(label)) {
                throw new IllegalArgumentException("관심 분야가 올바르지 않습니다: " + label);
            }
            unique.add(label);
        }
        return List.copyOf(unique);
    }
}
