package com.moabom.grants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Initial recommendation score. These weights are a starting point, not a value fit to evaluation data.
 *
 * <p>score = 0.6 * contentSimilarity + 0.4 * behaviorInterest
 *
 * <p>contentSimilarity is cosine similarity in [0, 1] between the user's interest weights and the
 * grant's category/keyword tokens. VIEW contributes 1 and SCRAP contributes 2 to each touched token.
 * A stated profile interest adds 1 to the content vector only and does not change behavior weights.
 * A grant token is 1. An empty interest profile or an empty grant token set yields 0.
 *
 * <p>behaviorInterest is in [0, 1]: 0.5 * directAction + 0.5 * categoryAffinity.
 * directAction is 1 when the user scrapped that grant, 0.5 when the user only viewed it, otherwise 0.
 * categoryAffinity is that category's accumulated weight divided by the user's maximum category weight.
 *
 * <p>Published numbers are rounded half up to 4 decimal places. Equal scores are ordered by
 * ELIGIBLE before CHECK_REQUIRED, then by grantId ascending.
 */
public final class RecommendationScorer {
    public static final double CONTENT_WEIGHT = 0.6;
    public static final double BEHAVIOR_WEIGHT = 0.4;
    public static final double VIEW_WEIGHT = 1.0;
    public static final double SCRAP_WEIGHT = 2.0;
    public static final double DIRECT_VIEW = 0.5;
    public static final double DIRECT_SCRAP = 1.0;
    public static final int TOP_N = 5;
    public static final int SCORE_SCALE = 4;

    private RecommendationScorer() {
    }

    public static List<RecommendationItem> recommend(
            UserProfile user,
            List<Grant> grants,
            List<UserBehavior> behaviors
    ) {
        Objects.requireNonNull(user, "user");
        List<Grant> catalog = grants == null ? List.of() : grants;
        List<UserBehavior> history = behaviors == null ? List.of() : behaviors;
        Interest interest = Interest.from(user, history, catalog);

        List<ScoredGrant> scored = new ArrayList<>();
        for (Grant grant : catalog) {
            if (grant == null || grant.grantId() == null || grant.grantId().isBlank()) {
                continue;
            }
            EligibilityAssessment assessment = EligibilityEvaluator.assess(user, grant.eligibility());
            if (assessment.status() == EligibilityStatus.INELIGIBLE) {
                continue;
            }
            double content = contentSimilarity(interest, grant);
            double behavior = behaviorInterest(interest, history, grant);
            double score = round(CONTENT_WEIGHT * content + BEHAVIOR_WEIGHT * behavior);
            scored.add(new ScoredGrant(
                    grant,
                    assessment,
                    round(content),
                    round(behavior),
                    score,
                    reasons(assessment, grant, interest, history, content, behavior)
            ));
        }

        scored.sort(Comparator
                .comparingDouble(ScoredGrant::score).reversed()
                .thenComparingInt(item -> item.assessment.tieRank())
                .thenComparing(item -> item.grant.grantId()));

        List<RecommendationItem> top = new ArrayList<>();
        int limit = Math.min(TOP_N, scored.size());
        for (int index = 0; index < limit; index++) {
            ScoredGrant item = scored.get(index);
            top.add(new RecommendationItem(
                    index + 1,
                    item.score,
                    item.contentSimilarity,
                    item.behaviorInterest,
                    item.assessment.status(),
                    item.assessment.checks(),
                    item.reasons,
                    item.grant
            ));
        }
        return List.copyOf(top);
    }

    private static double contentSimilarity(Interest interest, Grant grant) {
        List<String> tokens = grantTokens(grant);
        if (interest.tokenWeights.isEmpty() || tokens.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        for (String token : tokens) {
            dot += interest.tokenWeights.getOrDefault(token, 0.0);
        }
        double userNorm = 0.0;
        for (double weight : interest.tokenWeights.values()) {
            userNorm += weight * weight;
        }
        double grantNorm = tokens.size();
        if (userNorm == 0.0 || grantNorm == 0.0) {
            return 0.0;
        }
        return clamp(dot / (Math.sqrt(userNorm) * Math.sqrt(grantNorm)));
    }

    private static double behaviorInterest(Interest interest, List<UserBehavior> history, Grant grant) {
        double direct = directAction(history, grant.grantId());
        double categoryAffinity = 0.0;
        String category = categoryToken(grant.categoryTitle());
        if (category != null && interest.maxCategoryWeight > 0.0) {
            categoryAffinity = interest.categoryWeights.getOrDefault(category, 0.0) / interest.maxCategoryWeight;
        }
        return clamp(0.5 * direct + 0.5 * clamp(categoryAffinity));
    }

    private static double directAction(List<UserBehavior> history, String grantId) {
        boolean viewed = false;
        boolean scrapped = false;
        for (UserBehavior behavior : history) {
            if (behavior == null || !grantId.equals(behavior.grantId()) || behavior.type() == null) {
                continue;
            }
            if (behavior.type() == BehaviorType.SCRAP) {
                scrapped = true;
            } else if (behavior.type() == BehaviorType.VIEW) {
                viewed = true;
            }
        }
        if (scrapped) {
            return DIRECT_SCRAP;
        }
        if (viewed) {
            return DIRECT_VIEW;
        }
        return 0.0;
    }

    private static List<String> reasons(
            EligibilityAssessment assessment,
            Grant grant,
            Interest interest,
            List<UserBehavior> history,
            double content,
            double behavior
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("자격 검증: " + assessment.status().name());
        for (DimensionCheck check : assessment.checks()) {
            reasons.add(check.dimension() + " " + check.result().name() + " - " + check.detail());
        }
        if (interest.tokenWeights.isEmpty()) {
            reasons.add("행동 이력이 없어 콘텐츠 유사도와 행동 관심도를 0으로 두고 기본 추천했습니다.");
        } else {
            List<String> matched = matchedLabels(interest, grant);
            if (!matched.isEmpty()) {
                reasons.add("관심 분야와 겹칩니다: " + String.join(", ", matched));
            }
            if (directAction(history, grant.grantId()) == DIRECT_SCRAP) {
                reasons.add("이 지원금을 스크랩했습니다. 스크랩 가중치는 조회보다 높습니다.");
            } else if (directAction(history, grant.grantId()) == DIRECT_VIEW) {
                reasons.add("이 지원금을 조회했습니다.");
            }
            if (behavior > 0.0 && content == 0.0) {
                reasons.add("같은 카테고리의 행동 관심도가 반영되었습니다.");
            }
        }
        return List.copyOf(reasons);
    }

    private static List<String> matchedLabels(Interest interest, Grant grant) {
        List<String> labels = new ArrayList<>();
        String category = categoryToken(grant.categoryTitle());
        if (category != null && interest.tokenWeights.containsKey(category)) {
            labels.add(grant.categoryTitle().trim());
        }
        for (String keyword : grant.keywords()) {
            if (interest.tokenWeights.containsKey(keywordToken(keyword))) {
                labels.add(keyword);
            }
        }
        labels.sort(String::compareTo);
        return labels;
    }

    private static List<String> grantTokens(Grant grant) {
        List<String> tokens = new ArrayList<>();
        String category = categoryToken(grant.categoryTitle());
        if (category != null) {
            tokens.add(category);
        }
        for (String keyword : grant.keywords()) {
            String token = keywordToken(keyword);
            if (token != null && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        tokens.sort(String::compareTo);
        return tokens;
    }

    private static String categoryToken(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return "c:" + category.trim();
    }

    private static String keywordToken(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "k:" + keyword.trim();
    }

    private static double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    static double round(double value) {
        return new BigDecimal(Double.toString(clamp(value)))
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record ScoredGrant(
            Grant grant,
            EligibilityAssessment assessment,
            double contentSimilarity,
            double behaviorInterest,
            double score,
            List<String> reasons
    ) {
    }

    private static final class Interest {
        private final Map<String, Double> tokenWeights;
        private final Map<String, Double> categoryWeights;
        private final double maxCategoryWeight;

        private Interest(Map<String, Double> tokenWeights, Map<String, Double> categoryWeights) {
            this.tokenWeights = tokenWeights;
            this.categoryWeights = categoryWeights;
            this.maxCategoryWeight = categoryWeights.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        private static Interest from(UserProfile user, List<UserBehavior> behaviors, List<Grant> grants) {
            Map<String, Grant> byId = new HashMap<>();
            for (Grant grant : grants) {
                if (grant != null && grant.grantId() != null) {
                    byId.putIfAbsent(grant.grantId(), grant);
                }
            }
            Map<String, Double> tokens = new HashMap<>();
            Map<String, Double> categories = new HashMap<>();
            for (UserBehavior behavior : behaviors) {
                if (behavior == null || behavior.grantId() == null || behavior.type() == null) {
                    continue;
                }
                Grant grant = byId.get(behavior.grantId());
                if (grant == null) {
                    continue;
                }
                double weight = behavior.type() == BehaviorType.SCRAP ? SCRAP_WEIGHT : VIEW_WEIGHT;
                String category = categoryToken(grant.categoryTitle());
                if (category != null) {
                    tokens.merge(category, weight, Double::sum);
                    categories.merge(category, weight, Double::sum);
                }
                for (String keyword : grant.keywords()) {
                    String token = keywordToken(keyword);
                    if (token != null) {
                        tokens.merge(token, weight, Double::sum);
                    }
                }
            }
            Map<String, Double> contentTokens = new HashMap<>(tokens);
            for (String stated : user.interests()) {
                String category = categoryToken(stated);
                String keyword = keywordToken(stated);
                if (category != null) {
                    contentTokens.merge(category, 1.0, Double::sum);
                }
                if (keyword != null) {
                    contentTokens.merge(keyword, 1.0, Double::sum);
                }
            }
            return new Interest(contentTokens, categories);
        }
    }
}
