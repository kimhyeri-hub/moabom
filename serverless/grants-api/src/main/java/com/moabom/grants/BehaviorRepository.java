package com.moabom.grants;

import java.util.List;

public interface BehaviorRepository {
    List<UserBehavior> listByUser(String userId);

    BehaviorWriteResult saveBehavior(UserBehavior behavior);

    ScrapDeleteResult deleteScrap(String userId, String grantId);
}
