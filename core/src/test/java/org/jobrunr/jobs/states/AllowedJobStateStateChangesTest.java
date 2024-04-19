package org.jobrunr.jobs.states;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jobrunr.jobs.states.AllowedJobStateStateChanges.isAllowedStateChange;
import static org.jobrunr.jobs.states.AllowedJobStateStateChanges.isIllegalStateChange;
import static org.jobrunr.jobs.states.StateName.*;

class AllowedJobStateStateChangesTest {

    @Test
    void allowedStatesFromScheduled() {
        assertThat(isAllowedStateChange(SCHEDULED, SCHEDULED)).isTrue();
        assertThat(isAllowedStateChange(SCHEDULED, ENQUEUED)).isTrue();
        assertThat(isAllowedStateChange(SCHEDULED, FAILED)).isTrue();
        assertThat(isAllowedStateChange(SCHEDULED, SUCCEEDED)).isTrue();
        assertThat(isAllowedStateChange(SCHEDULED, DELETED)).isTrue();

        assertThat(isIllegalStateChange(SCHEDULED, PROCESSING)).isTrue();
    }

    @Test
    void allowedStatesFromEnqueued() {
        assertThat(isAllowedStateChange(ENQUEUED, SCHEDULED)).isTrue();
        assertThat(isAllowedStateChange(ENQUEUED, PROCESSING)).isTrue();
        assertThat(isAllowedStateChange(ENQUEUED, SUCCEEDED)).isTrue();
        assertThat(isAllowedStateChange(ENQUEUED, FAILED)).isTrue();
        assertThat(isAllowedStateChange(ENQUEUED, DELETED)).isTrue();

        assertThat(isIllegalStateChange(ENQUEUED, ENQUEUED)).isTrue();
    }

    @Test
    void allowedStatesFromProcessing() {
        assertThat(isAllowedStateChange(PROCESSING, SUCCEEDED)).isTrue();
        assertThat(isAllowedStateChange(PROCESSING, FAILED)).isTrue();
        assertThat(isAllowedStateChange(PROCESSING, DELETED)).isTrue();

        assertThat(isIllegalStateChange(PROCESSING, SCHEDULED)).isTrue();
        assertThat(isIllegalStateChange(PROCESSING, PROCESSING)).isTrue();
        assertThat(isIllegalStateChange(PROCESSING, ENQUEUED)).isTrue();
    }

    @Test
    void allowedStatesFromSucceeded() {
        assertThat(isAllowedStateChange(SUCCEEDED, SCHEDULED)).isTrue();
        assertThat(isAllowedStateChange(SUCCEEDED, ENQUEUED)).isTrue();
        assertThat(isAllowedStateChange(SUCCEEDED, DELETED)).isTrue();

        assertThat(isIllegalStateChange(SUCCEEDED, SUCCEEDED)).isTrue();
        assertThat(isIllegalStateChange(SUCCEEDED, PROCESSING)).isTrue();
        assertThat(isIllegalStateChange(SUCCEEDED, FAILED)).isTrue();
    }

    @Test
    void allowedStatesFromFailed() {
        assertThat(isAllowedStateChange(FAILED, SCHEDULED)).isTrue();
        assertThat(isAllowedStateChange(FAILED, ENQUEUED)).isTrue();
        assertThat(isAllowedStateChange(FAILED, DELETED)).isTrue();

        assertThat(isIllegalStateChange(FAILED, FAILED)).isTrue();
        assertThat(isIllegalStateChange(FAILED, PROCESSING)).isTrue();
        assertThat(isIllegalStateChange(FAILED, SUCCEEDED)).isTrue();
    }

    @Test
    void allowedStatesFromDeleted() {
        assertThat(isAllowedStateChange(DELETED, SCHEDULED)).isTrue();
        assertThat(isAllowedStateChange(DELETED, ENQUEUED)).isTrue();

        assertThat(isIllegalStateChange(DELETED, DELETED)).isTrue();
        assertThat(isIllegalStateChange(DELETED, FAILED)).isTrue();
        assertThat(isIllegalStateChange(DELETED, PROCESSING)).isTrue();
        assertThat(isIllegalStateChange(DELETED, SUCCEEDED)).isTrue();
    }

}