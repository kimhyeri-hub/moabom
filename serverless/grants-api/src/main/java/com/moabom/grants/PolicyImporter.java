package com.moabom.grants;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads manually supplied public-policy rows. It does not call an external API.
 * Blank eligibility stays unknown, and a value that is not a real calendar date is rejected.
 */
public final class PolicyImporter {
    private PolicyImporter() {
    }

    public static PolicyImportResult importJson(JsonNode records, List<Grant> existing) {
        if (records == null || !records.isArray()) {
            throw new IllegalArgumentException("json 형식은 records 배열이 필요합니다.");
        }
        if (records.isEmpty()) {
            throw new IllegalArgumentException("가져올 정책 행이 없습니다.");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode record : records) {
            if (record == null || !record.isObject()) {
                rows.add(Map.of());
                continue;
            }
            rows.add(flatten(record));
        }
        return importRows(rows, existing, 1);
    }

    public static PolicyImportResult importCsv(String csv, List<Grant> existing) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("가져올 정책 행이 없습니다.");
        }
        String text = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        List<List<String>> table = parseCsv(text);
        if (table.isEmpty()) {
            throw new IllegalArgumentException("가져올 정책 행이 없습니다.");
        }
        List<String> header = table.get(0);
        if (!header.contains("serviceId") || !header.contains("serviceName")) {
            throw new IllegalArgumentException("CSV 헤더에 serviceId와 serviceName이 필요합니다.");
        }
        if (table.size() == 1) {
            throw new IllegalArgumentException("가져올 정책 행이 없습니다.");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < table.size(); index++) {
            List<String> cells = table.get(index);
            if (cells.stream().allMatch(cell -> cell == null || cell.isBlank())) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) {
                String name = header.get(column) == null ? "" : header.get(column).trim();
                if (name.isEmpty()) {
                    continue;
                }
                String value = column < cells.size() ? cells.get(column) : "";
                row.put(name, value);
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("가져올 정책 행이 없습니다.");
        }
        return importRows(rows, existing, 2);
    }

    private static PolicyImportResult importRows(List<Map<String, String>> rows, List<Grant> existing, int firstRowNumber) {
        List<Grant> imported = new ArrayList<>();
        List<PolicyImportIssue> errors = new ArrayList<>();
        List<PolicyImportIssue> warnings = new ArrayList<>();
        List<List<String>> duplicateGroups = new ArrayList<>();
        List<Grant> baseline = new ArrayList<>(existing == null ? List.of() : existing);
        for (int index = 0; index < rows.size(); index++) {
            int rowNumber = firstRowNumber + index;
            acceptRow(rows.get(index), rowNumber, baseline, imported, errors, warnings, duplicateGroups);
        }
        return new PolicyImportResult(
                List.copyOf(imported),
                List.copyOf(errors),
                List.copyOf(warnings),
                List.copyOf(duplicateGroups)
        );
    }

    private static void acceptRow(
            Map<String, String> row,
            int rowNumber,
            List<Grant> baseline,
            List<Grant> imported,
            List<PolicyImportIssue> errors,
            List<PolicyImportIssue> warnings,
            List<List<String>> duplicateGroups
    ) {
        if (row == null || row.isEmpty()) {
            errors.add(new PolicyImportIssue(rowNumber, "row", "정책 행이 객체가 아닙니다."));
            return;
        }
        List<PolicyImportIssue> rowErrors = new ArrayList<>();
        String serviceId = text(row, "serviceId");
        String serviceName = text(row, "serviceName");
        if (serviceId == null) {
            rowErrors.add(new PolicyImportIssue(rowNumber, "serviceId", "서비스 ID는 필수입니다."));
        }
        if (serviceName == null) {
            rowErrors.add(new PolicyImportIssue(rowNumber, "serviceName", "서비스명은 필수입니다."));
        }
        String referenceDate = date(row, "referenceDate", "기준일", rowNumber, rowErrors);
        String applicationStart = date(row, "applicationStart", "신청 시작일", rowNumber, rowErrors);
        String applicationEnd = date(row, "applicationEnd", "신청 마감일", rowNumber, rowErrors);
        Integer minAge = integer(row, "minAge", "최소 연령", 0, 120, rowNumber, rowErrors);
        Integer maxAge = integer(row, "maxAge", "최대 연령", 0, 120, rowNumber, rowErrors);
        Integer minIncome = integer(row, "minIncomeBracket", "최소 소득분위", 1, 10, rowNumber, rowErrors);
        Integer maxIncome = integer(row, "maxIncomeBracket", "최대 소득분위", 1, 10, rowNumber, rowErrors);
        if (minAge != null && maxAge != null && minAge > maxAge) {
            rowErrors.add(new PolicyImportIssue(rowNumber, "minAge", "최소 연령이 최대 연령보다 큽니다. 범위를 추정하지 않습니다."));
        }
        if (minIncome != null && maxIncome != null && minIncome > maxIncome) {
            rowErrors.add(new PolicyImportIssue(rowNumber, "minIncomeBracket", "최소 소득분위가 최대 소득분위보다 큽니다. 범위를 추정하지 않습니다."));
        }
        RequirementClarity householdClarity = clarity(row, "householdClarity", "가구 형태", rowNumber, warnings);
        RequirementClarity specialClarity = clarity(row, "specialClarity", "특수조건", rowNumber, warnings);
        List<String> households = codes(row, "householdTypes", PolicyCodes.HOUSEHOLDS, "가구 형태", rowNumber, rowErrors);
        List<String> specials = codes(row, "specialConditions", PolicyCodes.SPECIALS, "특수조건", rowNumber, rowErrors);
        Boolean sample = sampleFlag(row, rowNumber, rowErrors);
        if (!rowErrors.isEmpty() || households == null || specials == null || sample == null) {
            errors.addAll(rowErrors);
            return;
        }
        if (householdClarity == RequirementClarity.SPECIFIED && households.isEmpty()) {
            warnings.add(new PolicyImportIssue(rowNumber, "householdTypes", "가구 형태가 SPECIFIED이지만 코드가 없어 제한 없음으로 보지 않고 정보 없음으로 저장합니다."));
        }
        if (specialClarity == RequirementClarity.SPECIFIED && specials.isEmpty()) {
            warnings.add(new PolicyImportIssue(rowNumber, "specialConditions", "특수조건이 SPECIFIED이지만 코드가 없어 제한 없음으로 보지 않고 정보 없음으로 저장합니다."));
        }
        PublicPolicyRecord record = new PublicPolicyRecord(
                text(row, "sourceName"),
                serviceId,
                serviceName,
                text(row, "summary"),
                firstText(row, "serviceUrl", "sourceUrl"),
                referenceDate,
                text(row, "referenceText"),
                applicationStart,
                applicationEnd,
                text(row, "regionText"),
                minAge,
                maxAge,
                minIncome,
                maxIncome,
                householdClarity,
                households,
                specialClarity,
                specials,
                sample,
                firstText(row, "applicationLink", "link")
        );
        Grant candidate;
        try {
            candidate = PublicPolicyMapper.toGrant(record);
        } catch (IllegalArgumentException ex) {
            errors.add(new PolicyImportIssue(rowNumber, "row", ex.getMessage()));
            return;
        }
        List<String> matched = new ArrayList<>();
        for (Grant current : baseline) {
            if (current != null && GrantNormalizer.samePolicy(current, candidate)) {
                matched.add(current.grantId());
            }
        }
        if (!matched.isEmpty()) {
            matched.add(candidate.grantId());
            matched.sort(String::compareTo);
            duplicateGroups.add(List.copyOf(matched));
            errors.add(new PolicyImportIssue(
                    rowNumber,
                    "duplicate",
                    "같은 정책으로 판별되어 저장하지 않았습니다: " + String.join(", ", matched)
            ));
            return;
        }
        boolean idTaken = baseline.stream().anyMatch(current -> current != null && candidate.grantId().equals(current.grantId()));
        if (idTaken) {
            errors.add(new PolicyImportIssue(rowNumber, "serviceId", "지원금 ID가 이미 있습니다: " + candidate.grantId()));
            return;
        }
        imported.add(candidate);
        baseline.add(candidate);
    }

    private static Map<String, String> flatten(JsonNode object) {
        Map<String, String> row = new LinkedHashMap<>();
        object.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isArray()) {
                List<String> parts = new ArrayList<>();
                value.forEach(item -> {
                    if (item != null && !item.isNull() && !item.asText().isBlank()) {
                        parts.add(item.asText().trim());
                    }
                });
                row.put(entry.getKey(), String.join("|", parts));
                return;
            }
            row.put(entry.getKey(), value.asText());
        });
        return row;
    }

    private static String text(Map<String, String> row, String field) {
        String value = row.get(field);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String firstText(Map<String, String> row, String primary, String alias) {
        String value = text(row, primary);
        return value == null ? text(row, alias) : value;
    }

    private static String date(
            Map<String, String> row,
            String field,
            String label,
            int rowNumber,
            List<PolicyImportIssue> errors
    ) {
        String value = text(row, field);
        if (value == null) {
            return null;
        }
        try {
            LocalDate.parse(value);
            return value;
        } catch (DateTimeParseException ex) {
            errors.add(new PolicyImportIssue(
                    rowNumber,
                    field,
                    label + "은 yyyy-MM-dd 형식만 저장합니다. 입력값을 다른 날짜로 바꾸지 않습니다: " + value
            ));
            return null;
        }
    }

    private static Integer integer(
            Map<String, String> row,
            String field,
            String label,
            int min,
            int max,
            int rowNumber,
            List<PolicyImportIssue> errors
    ) {
        String value = text(row, field);
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                errors.add(new PolicyImportIssue(rowNumber, field, label + " 값이 허용 범위를 벗어났습니다. 값을 추정하지 않습니다: " + value));
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            errors.add(new PolicyImportIssue(rowNumber, field, label + " 값은 정수이거나 비어 있어야 합니다. 값을 추정하지 않습니다: " + value));
            return null;
        }
    }

    private static RequirementClarity clarity(
            Map<String, String> row,
            String field,
            String label,
            int rowNumber,
            List<PolicyImportIssue> warnings
    ) {
        String value = text(row, field);
        if (value == null) {
            return null;
        }
        try {
            return RequirementClarity.valueOf(value);
        } catch (IllegalArgumentException ex) {
            warnings.add(new PolicyImportIssue(
                    rowNumber,
                    field,
                    label + " 값을 해석할 수 없어 정보 없음으로 저장합니다. 제한 없음으로 바꾸지 않습니다: " + value
            ));
            return null;
        }
    }

    private static List<String> codes(
            Map<String, String> row,
            String field,
            java.util.Set<String> allowed,
            String label,
            int rowNumber,
            List<PolicyImportIssue> errors
    ) {
        String value = text(row, field);
        if (value == null) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split("[|,]")) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        try {
            return PolicyCodes.normalize(parts, allowed, label);
        } catch (IllegalArgumentException ex) {
            errors.add(new PolicyImportIssue(rowNumber, field, ex.getMessage()));
            return null;
        }
    }

    private static Boolean sampleFlag(Map<String, String> row, int rowNumber, List<PolicyImportIssue> errors) {
        if (!row.containsKey("sample") || row.get("sample") == null || row.get("sample").isBlank()) {
            return false;
        }
        String value = row.get("sample").trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        errors.add(new PolicyImportIssue(rowNumber, "sample", "sample은 true 또는 false여야 합니다. 테스트 데이터로 추정하지 않습니다: " + row.get("sample").trim()));
        return null;
    }

    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++) {
            char current = csv.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(current);
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (current == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (current != '\r') {
                cell.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV 따옴표가 닫히지 않았습니다.");
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
