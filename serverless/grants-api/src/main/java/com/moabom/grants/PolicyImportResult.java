package com.moabom.grants;

import java.util.List;

public record PolicyImportResult(
        List<Grant> imported,
        List<PolicyImportIssue> errors,
        List<PolicyImportIssue> warnings,
        List<List<String>> duplicateGroups
) {
}
