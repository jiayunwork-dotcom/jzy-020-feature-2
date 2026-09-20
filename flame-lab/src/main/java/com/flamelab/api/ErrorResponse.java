package com.flamelab.api;

import com.flamelab.job.ErrorInfo;

/** Uniform API error body: {"error": {"type": ..., "message": ...}}. */
public record ErrorResponse(ErrorInfo error) {
}
