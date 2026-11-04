package com.moabom.grants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GrantsRepository {
    GrantPage listGrants(int limit, String exclusiveStartGrantId);

    Optional<Grant> getGrant(String grantId);

    /**
     * Reads the GRANT partition with Query pages. Stops at 500 items so recommendation
     * does not scan the table or follow an unbounded result set.
     */
    default List<Grant> listAllGrants() {
        List<Grant> all = new ArrayList<>();
        Set<String> seenTokens = new HashSet<>();
        String token = null;
        for (int page = 0; page < 20 && all.size() < 500; page++) {
            GrantPage result = listGrants(100, token);
            List<Grant> items = result.grants() == null ? List.of() : result.grants();
            all.addAll(items);
            String next = result.nextToken();
            if (next == null || items.isEmpty() || !seenTokens.add(next)) {
                break;
            }
            token = next;
        }
        if (all.size() > 500) {
            return List.copyOf(all.subList(0, 500));
        }
        return List.copyOf(all);
    }
}
