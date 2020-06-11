package org.jobrunr.jobs.mappers;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobParameter;
import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.states.ProcessingState;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.runner.RunnerJobContext;
import org.jobrunr.stubs.TestService;
import org.jobrunr.utils.mapper.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.jobrunr.JobRunrAssertions.assertThat;
import static org.jobrunr.jobs.JobDetailsTestBuilder.jobDetails;
import static org.jobrunr.jobs.JobTestBuilder.anEnqueuedJob;
import static org.jobrunr.jobs.RecurringJobTestBuilder.aDefaultRecurringJob;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
abstract class JobMapperTest {

    @Mock
    private BackgroundJobServer backgroundJobServer;

    private TestService testService;

    private JobMapper jobMapper;

    @BeforeEach
    void setUp() {
        jobMapper = new JobMapper(getJsonMapper());
        testService = new TestService();

        lenient().when(backgroundJobServer.getId()).thenReturn(UUID.randomUUID());
    }

    protected abstract JsonMapper getJsonMapper();

    @Test
    void testSerializeAndDeserializeJob() {
        Job job = anEnqueuedJob()
                .withVersion(2)
                .build();

        String jobAsString = jobMapper.serializeJob(job);
        final Job actualJob = jobMapper.deserializeJob(jobAsString);

        assertThat(actualJob).isEqualTo(job);
    }

    @Test
    void testSerializeAndDeserializeProcessingJobWithLogs() {
        Job job = anEnqueuedJob().withState(new ProcessingState(UUID.randomUUID())).build();
        final RunnerJobContext jobContext = new RunnerJobContext(job);
        jobContext.logger().info("test 1");
        jobContext.logger().warn("test 2");

        String jobAsString = jobMapper.serializeJob(job);
        System.out.println(jobAsString);
        final Job actualJob = jobMapper.deserializeJob(jobAsString);

        assertThat(actualJob).isEqualTo(job);
        throw new UnsupportedOperationException("todo add progress bar and assert content?");
    }

    @Test
    void testSerializeAndDeserializeJobWithPath() {
        Job job = anEnqueuedJob().withJobDetails(() -> testService.doWorkWithPath(Path.of("/tmp", "jobrunr", "log.txt"))).build();

        String jobAsString = jobMapper.serializeJob(job);

        assertThatCode(() -> jobMapper.deserializeJob(jobAsString)).doesNotThrowAnyException();
    }

    @Test
    void serializeAndDeserializeRecurringJob() {
        RecurringJob recurringJob = aDefaultRecurringJob().build();

        String jobAsString = jobMapper.serializeRecurringJob(recurringJob);
        final RecurringJob actualRecurringJob = jobMapper.deserializeRecurringJob(jobAsString);

        assertThat(actualRecurringJob).isEqualTo(recurringJob);
    }

    @Test
    void canSerializeAndDeserializeWithJobContext() {
        Job job = anEnqueuedJob()
                .withJobDetails(jobDetails()
                        .withClassName(TestService.class)
                        .withMethodName("doWork")
                        .withJobParameter(5)
                        .withJobParameter(JobParameter.JobContext)
                )
                .build();

        String jobAsJson = jobMapper.serializeJob(job);
        Job actualJob = jobMapper.deserializeJob(jobAsJson);

        assertThat(actualJob).isEqualTo(job);
    }

    @Test
    void canSerializeAndDeserializeJobWithAllStatesAndMetadata() {
        Job job = anEnqueuedJob()
                .withMetadata("metadata1", new TestMetadata("input"))
                .withMetadata("metadata2", "a string")
                .withMetadata("metadata3", 15.0)
                .build();
        job.startProcessingOn(backgroundJobServer);
        job.failed("exception", new Exception("Test"));
        job.succeeded();

        String jobAsJson = jobMapper.serializeJob(job);
        Job actualJob = jobMapper.deserializeJob(jobAsJson);

        assertThat(actualJob).isEqualTo(job);
    }

    public static class TestMetadata implements JobContext.Metadata {
        private String input;
        private Instant instant;

        private TestMetadata() {
        }

        public TestMetadata(String input) {
            this.input = input;
            this.instant = Instant.now();
        }

        public String getInput() {
            return input;
        }

        public Instant getInstant() {
            return instant;
        }
    }
}