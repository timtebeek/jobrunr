package org.jobrunr.jobs;

import org.junit.jupiter.api.Test;

import static org.jobrunr.JobRunrAssertions.assertThat;
import static org.jobrunr.jobs.JobTestBuilder.aScheduledJob;

class JobVersionerTest {

    @Test
    void jobVersionerOnCommitVersionIsIncreased() {
        // GIVEN
        Job job = aScheduledJob().withVersion(5).build();

        // WHEN
        JobVersioner jobVersioner = new JobVersioner(job);

        // THEN
        assertThat(job).hasVersion(6);

        // WHEN
        jobVersioner.commitVersion();

        // THEN
        assertThat(job).hasVersion(6);
    }

    @Test
    void jobVersionerOnRollbackVersionIsRestored() {
        // GIVEN
        Job job = aScheduledJob().withVersion(5).build();

        // WHEN
        JobVersioner jobVersioner = new JobVersioner(job);

        // THEN
        assertThat(job).hasVersion(6);

        // WHEN
        jobVersioner.close();

        // THEN
        assertThat(job).hasVersion(5);
    }

    @Test
    void jobVersionerInTryWithResourcesOnRollbackVersionIsRestored() {
        Job job = aScheduledJob().withVersion(5).build();
        try (JobVersioner jobVersioner = new JobVersioner(job)) {
            // nothing to do as not committed
        }

        assertThat(job).hasVersion(5);
    }
}