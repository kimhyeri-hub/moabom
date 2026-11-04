package com.moabom.grants;

import java.util.List;

/**
 * API representation of one grant.
 * Django Grant fields are grantId, categoryTitle, title, description, endDate, and link.
 * sourceUrl, referenceDate, referenceText, applicationStart, and sourceServiceId describe provenance.
 * sample is true for fictional catalog rows so they stay distinct from public policy records.
 */
public record Grant(
        String grantId,
        String categoryTitle,
        String title,
        String description,
        String endDate,
        String link,
        Eligibility eligibility,
        boolean sample,
        List<String> keywords,
        String sourceUrl,
        String referenceDate,
        String referenceText,
        String applicationStart,
        String sourceServiceId
) {
    public Grant {
        keywords = keywords == null
                ? List.of()
                : keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        sourceUrl = blankToNull(sourceUrl);
        referenceDate = blankToNull(referenceDate);
        referenceText = blankToNull(referenceText);
        applicationStart = blankToNull(applicationStart);
        sourceServiceId = blankToNull(sourceServiceId);
    }

    public Grant(
            String grantId,
            String categoryTitle,
            String title,
            String description,
            String endDate,
            String link,
            Eligibility eligibility,
            boolean sample,
            List<String> keywords
    ) {
        this(
                grantId,
                categoryTitle,
                title,
                description,
                endDate,
                link,
                eligibility,
                sample,
                keywords,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
