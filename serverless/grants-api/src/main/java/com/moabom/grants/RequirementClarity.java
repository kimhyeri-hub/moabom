package com.moabom.grants;

/**
 * How clearly a policy states one eligibility dimension.
 * UNKNOWN means the source did not say, and the value must not be inferred.
 */
public enum RequirementClarity {
    UNRESTRICTED,
    SPECIFIED,
    UNKNOWN
}
