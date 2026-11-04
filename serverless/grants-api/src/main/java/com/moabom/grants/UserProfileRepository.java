package com.moabom.grants;

import java.util.Optional;

public interface UserProfileRepository {
    Optional<UserProfile> getProfile(String userId);

    void saveProfile(UserProfile profile);
}
