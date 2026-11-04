package com.moabom.grants;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class InMemoryBehaviorRepository implements BehaviorRepository {
    private final List<UserBehavior> behaviors;

    public InMemoryBehaviorRepository(List<UserBehavior> behaviors) {
        this.behaviors = behaviors;
    }

    @Override
    public List<UserBehavior> listByUser(String userId) {
        return behaviors.stream()
                .filter(behavior -> userId.equals(behavior.userId()))
                .sorted(Comparator
                        .comparing(UserBehavior::occurredAt)
                        .thenComparing(behavior -> behavior.type().name())
                        .thenComparing(UserBehavior::grantId))
                .toList();
    }

    @Override
    public BehaviorWriteResult saveBehavior(UserBehavior behavior) {
        for (UserBehavior stored : behaviors) {
            if (sameAction(stored, behavior.userId(), behavior.type(), behavior.grantId())) {
                return new BehaviorWriteResult(stored, false);
            }
        }
        behaviors.add(behavior);
        return new BehaviorWriteResult(behavior, true);
    }

    @Override
    public ScrapDeleteResult deleteScrap(String userId, String grantId) {
        Iterator<UserBehavior> iterator = behaviors.iterator();
        boolean deleted = false;
        while (iterator.hasNext()) {
            UserBehavior stored = iterator.next();
            if (sameAction(stored, userId, BehaviorType.SCRAP, grantId)) {
                iterator.remove();
                deleted = true;
            }
        }
        return new ScrapDeleteResult(deleted);
    }

    private static boolean sameAction(UserBehavior stored, String userId, BehaviorType type, String grantId) {
        return stored.userId().equals(userId) && stored.type() == type && stored.grantId().equals(grantId);
    }
}
