package com.flamelab.job;

/** A typed error with a human-readable message; serialized into job records and API error bodies. */
public record ErrorInfo(ErrorType type, String message) {
}
