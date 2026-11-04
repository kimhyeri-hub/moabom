package com.moabom.grants;

import java.util.ArrayList;
import java.util.List;

/** Groups grants that refer to the same policy after normalization. */
public final class PolicyDuplicateFinder {
    private PolicyDuplicateFinder() {
    }

    public static List<List<String>> duplicateGroups(List<Grant> grants) {
        List<Grant> present = grants == null ? List.of() : grants.stream().filter(grant -> grant != null).toList();
        int size = present.size();
        int[] parent = new int[size];
        for (int index = 0; index < size; index++) {
            parent[index] = index;
        }
        for (int left = 0; left < size; left++) {
            for (int right = left + 1; right < size; right++) {
                if (GrantNormalizer.samePolicy(present.get(left), present.get(right))) {
                    union(parent, left, right);
                }
            }
        }
        List<List<Integer>> buckets = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            buckets.add(new ArrayList<>());
        }
        for (int index = 0; index < size; index++) {
            buckets.get(find(parent, index)).add(index);
        }
        List<List<String>> groups = new ArrayList<>();
        for (List<Integer> bucket : buckets) {
            if (bucket.size() < 2) {
                continue;
            }
            List<String> ids = new ArrayList<>();
            for (int index : bucket) {
                ids.add(present.get(index).grantId());
            }
            ids.sort(String::compareTo);
            groups.add(List.copyOf(ids));
        }
        groups.sort((left, right) -> left.get(0).compareTo(right.get(0)));
        return List.copyOf(groups);
    }

    private static int find(int[] parent, int index) {
        if (parent[index] != index) {
            parent[index] = find(parent, parent[index]);
        }
        return parent[index];
    }

    private static void union(int[] parent, int left, int right) {
        int leftRoot = find(parent, left);
        int rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            parent[rightRoot] = leftRoot;
        }
    }
}
