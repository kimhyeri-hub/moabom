package com.moabom.grants;

import java.util.List;

/** One page of the grant catalog. nextToken is null when there is no further page. */
public record GrantPage(List<Grant> grants, String nextToken) {
}
