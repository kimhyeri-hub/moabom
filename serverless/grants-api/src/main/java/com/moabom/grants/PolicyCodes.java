package com.moabom.grants;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Codes shared by the React tag buttons and the eligibility model. */
public final class PolicyCodes {
    public static final Set<String> HOUSEHOLDS = Set.of(
            "SINGLE",
            "NEWLYWED",
            "MULTI_CHILD",
            "SINGLE_PARENT"
    );
    public static final Set<String> SPECIALS = Set.of(
            "DISABLED",
            "VETERAN",
            "NEWLYWED",
            "MULTI_CHILD",
            "OTHER"
    );

    private PolicyCodes() {
    }

    public static List<String> normalize(List<String> values, Set<String> allowed, String label) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        TreeSet<String> unique = new TreeSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String code = value.trim();
            if (!allowed.contains(code)) {
                throw new IllegalArgumentException(label + " 코드가 올바르지 않습니다: " + code);
            }
            unique.add(code);
        }
        return List.copyOf(new ArrayList<>(unique));
    }
}
