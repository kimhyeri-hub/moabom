package com.moabom.grants;

public final class BehaviorKeys {
    private BehaviorKeys() {
    }

    public static String behaviorSk(BehaviorType type, String grantId) {
        return type.name() + "#" + grantId;
    }
}
