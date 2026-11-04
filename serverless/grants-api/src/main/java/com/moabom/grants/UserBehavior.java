package com.moabom.grants;

public record UserBehavior(
        String userId,
        BehaviorType type,
        String grantId,
        String occurredAt
) {
}
