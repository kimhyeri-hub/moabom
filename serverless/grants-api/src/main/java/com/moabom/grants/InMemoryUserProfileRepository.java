package com.moabom.grants;

import java.util.Map;
import java.util.Optional;

public final class InMemoryUserProfileRepository implements UserProfileRepository {
    private final Map<String, UserProfile> profiles;

    public InMemoryUserProfileRepository(Map<String, UserProfile> profiles) {
        this.profiles = profiles;
    }

    @Override
    public Optional<UserProfile> getProfile(String userId) {
        return Optional.ofNullable(profiles.get(userId));
    }

    @Override
    public void saveProfile(UserProfile profile) {
        profiles.put(profile.userId(), profile);
    }
}
