package com.flamelab.job;

/** Typed errors, both for rejected requests and for failed jobs. */
public enum ErrorType {

    // Request validation (HTTP 400, no job is created)
    MISSING_FIELD,
    NON_FINITE_VALUE,
    NON_POSITIVE_EQUIVALENCE_RATIO,
    NON_POSITIVE_INTAKE_TEMPERATURE,
    NON_POSITIVE_TARGET_TEMPERATURE,
    ILLEGAL_SIDE,
    UNKNOWN_FUEL,
    MALFORMED_REQUEST,

    // Lookup (HTTP 404)
    JOB_NOT_FOUND,

    // Solve-time failures (job is persisted with status FAILED)
    TEMPERATURE_OUT_OF_RANGE,
    ENTHALPY_NOT_CONVERGED,
    ELEMENT_BALANCE_VIOLATION,
    TARGET_UNREACHABLE,
    SIDE_NOT_CONVERGED,

    INTERNAL_ERROR
}
