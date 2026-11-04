package com.moabom.grants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class InMemoryGrantsRepository implements GrantsRepository {
    private final List<Grant> grants;

    public InMemoryGrantsRepository(List<Grant> grants) {
        this.grants = new ArrayList<>(grants.stream()
                .sorted(Comparator.comparing(Grant::grantId))
                .toList());
    }

    public void add(Grant grant) {
        grants.add(grant);
        grants.sort(Comparator.comparing(Grant::grantId));
    }

    @Override
    public GrantPage listGrants(int limit, String exclusiveStartGrantId) {
        List<Grant> remaining = grants.stream()
                .filter(grant -> exclusiveStartGrantId == null
                        || grant.grantId().compareTo(exclusiveStartGrantId) > 0)
                .toList();
        List<Grant> page = remaining.stream().limit(limit).toList();
        String nextToken = remaining.size() > page.size()
                ? page.get(page.size() - 1).grantId()
                : null;
        return new GrantPage(page, nextToken);
    }

    @Override
    public Optional<Grant> getGrant(String grantId) {
        return grants.stream()
                .filter(grant -> grant.grantId().equals(grantId))
                .findFirst();
    }
}
