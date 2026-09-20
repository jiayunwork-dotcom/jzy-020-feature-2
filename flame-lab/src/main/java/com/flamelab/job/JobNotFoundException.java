package com.flamelab.job;

/** Raised when a job id does not exist; mapped to HTTP 404. */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(long id) {
        super("no job with id " + id);
    }
}
