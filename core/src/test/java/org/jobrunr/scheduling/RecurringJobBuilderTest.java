package org.jobrunr.scheduling;

import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.jobs.details.JobDetailsAsmGenerator;
import org.jobrunr.jobs.details.JobDetailsGenerator;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.stubs.TestJobRequest;
import org.jobrunr.stubs.TestService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static java.time.ZoneId.systemDefault;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.jobrunr.JobRunrAssertions.assertThat;
import static org.jobrunr.scheduling.RecurringJobBuilder.aRecurringJob;

class RecurringJobBuilderTest {

    private static final String every5Seconds = "*/5 * * * * *";
    private static final String duration1Minute = "PT1M";

    private final JobDetailsGenerator jobDetailsGenerator = new JobDetailsAsmGenerator();
    private final JobRequest jobRequest = new TestJobRequest("Not important");

    private TestService testService;

    @Test
    void defaultJobWithJobLambda() {
        RecurringJob recurringJob = aRecurringJob()
                .withDetails(() -> testService.doWork())
                .withCron(every5Seconds)
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasId()
                .hasScheduleExpression(every5Seconds)
                .hasJobDetails(TestService.class, "doWork");
    }

    @Test
    void defaultJobWithIoCJobLambda() {
        RecurringJob recurringJob = aRecurringJob()
                .<TestService>withDetails(x -> x.doWork())
                .withCron(every5Seconds)
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasId()
                .hasScheduleExpression(every5Seconds)
                .hasJobDetails(TestService.class, "doWork");
    }

    @Test
    void defaultJobWithJobRequest() {
        RecurringJob recurringJob = aRecurringJob()
                .withJobRequest(jobRequest)
                .withCron(every5Seconds)
                .build();

        assertThat(recurringJob)
                .hasId()
                .hasScheduleExpression(every5Seconds)
                .hasJobDetails(TestJobRequest.TestJobRequestHandler.class, "run", jobRequest);
    }

    @Test
    void withId() {
        String id = UUID.randomUUID().toString();
        RecurringJob recurringJob = aRecurringJob()
                .withId(id)
                .withDetails(() -> testService.doWork())
                .withCron(every5Seconds)
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasId(id)
                .hasScheduleExpression(every5Seconds);
    }

    @Test
    void withJobName() {
        RecurringJob recurringJob = aRecurringJob()
                .withDetails(() -> testService.doWork())
                .withCron(every5Seconds)
                .withName("My job name")
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasJobName("My job name")
                .hasId()
                .hasScheduleExpression(every5Seconds);
    }

    @Test
    void withAmountOfRetries() {
        RecurringJob recurringJob = aRecurringJob()
                .withAmountOfRetries(10)
                .withCron(every5Seconds)
                .withDetails(() -> testService.doWork())
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasRetries(10)
                .hasId()
                .hasScheduleExpression(every5Seconds);
    }

    @Test
    void withLabels() {
        RecurringJob recurringJob = aRecurringJob()
                .withLabels(Set.of("TestLabel", "Email"))
                .withCron(every5Seconds)
                .withDetails(() -> testService.doWork())
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasLabels(Set.of("TestLabel", "Email"))
                .hasId()
                .hasScheduleExpression(every5Seconds);
    }

    @Test
    void maxAmountOfLabels() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withLabels("TestLabel", "Email", "Automated", "Too many")
                        .withCron(every5Seconds)
                        .withDetails(() -> testService.doWork())
                        .build(jobDetailsGenerator));
    }

    @Test
    void maxLengthOfLabel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withLabels("Label longer than 45 characters should throw an exception")
                        .withCron(every5Seconds)
                        .withDetails(() -> testService.doWork())
                        .build(jobDetailsGenerator));
    }

    @Test
    void withDuration() {
        RecurringJob recurringJob = aRecurringJob()
                .withDuration(Duration.ofMinutes(1))
                .withDetails(() -> testService.doWork())
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasId()
                .hasScheduleExpression(duration1Minute);
    }

    @Test
    void withZoneId() {
        RecurringJob recurringJob = aRecurringJob()
                .withZoneId(ZoneId.of("Europe/Brussels"))
                .withCron(every5Seconds)
                .withDetails(() -> testService.doWork())
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasZoneId("Europe/Brussels")
                .hasId()
                .hasScheduleExpression(every5Seconds);
    }

    @Test
    void withDefaultZoneId() {
        RecurringJob recurringJob = aRecurringJob()
                .withCron(every5Seconds)
                .withDetails(() -> testService.doWork())
                .build(jobDetailsGenerator);

        assertThat(recurringJob)
                .hasZoneId(systemDefault().toString())
                .hasId()
                .hasScheduleExpression(every5Seconds);
    }

    @Test
    void jobDetailsCanOnlyBeSet1Way() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withDetails(() -> testService.doWork())
                        .withJobRequest(jobRequest)
                        .withCron(every5Seconds)
                        .build(jobDetailsGenerator));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withJobRequest(jobRequest)
                        .withDetails(() -> testService.doWork())
                        .withCron(every5Seconds)
                        .build(jobDetailsGenerator));
    }

    @Test
    void buildWithIncorrectJobDetails() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withJobRequest(jobRequest)
                        .withCron(every5Seconds)
                        .build(jobDetailsGenerator));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withDetails(() -> testService.doWork())
                        .withCron(every5Seconds)
                        .build());
    }

    @Test
    void buildWithoutSchedule() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withJobRequest(jobRequest)
                        .build());
    }

    @Test
    void scheduleCanOnlyBeSet1Way() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withCron(every5Seconds)
                        .withDuration(Duration.ofMinutes(1))
                        .withJobRequest(jobRequest)
                        .build());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> aRecurringJob()
                        .withDuration(Duration.ofMinutes(1))
                        .withCron(every5Seconds)
                        .withJobRequest(jobRequest)
                        .build());
    }
}