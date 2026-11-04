package com.moabom.grants;

import java.util.List;

public record EligibilityAssessment(EligibilityStatus status, List<DimensionCheck> checks) {
    public int tieRank() {
        return status == EligibilityStatus.ELIGIBLE ? 0 : 1;
    }
}
