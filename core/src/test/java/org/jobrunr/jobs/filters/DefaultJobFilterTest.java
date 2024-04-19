package org.jobrunr.jobs.filters;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobParameter;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.stubs.TestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jobrunr.JobRunrAssertions.assertThat;
import static org.jobrunr.jobs.JobDetailsTestBuilder.jobDetails;
import static org.jobrunr.jobs.JobDetailsTestBuilder.systemOutPrintLnJobDetails;
import static org.jobrunr.jobs.JobTestBuilder.anEnqueuedJob;

class DefaultJobFilterTest {

    private DefaultJobFilter defaultJobFilter;
    private TestService testService;

    @BeforeEach
    void setup() {
        defaultJobFilter = new DefaultJobFilter();
    }

    @Test
    void displayNameByAnnotationReplacesVariables() {
        Job job = anEnqueuedJob().withoutName()
                .withJobDetails(() -> testService.doWorkWithAnnotationAndJobContext(67656, "the almighty user", JobContext.Null))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job).hasJobName("Doing some hard work for user the almighty user with id 67656");
    }

    @Test
    void displayNameIsUsedIfProvidedByJobBuilder() {
        Job job = anEnqueuedJob()
                .withName("My job name")
                .withJobDetails(jobDetails()
                        .withClassName(TestService.class)
                        .withMethodName("doWork")
                        .withJobParameter(2))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job).hasJobName("My job name");
    }

    @Test
    void amountOfRetriesIsUsedIfProvidedByJobBuilder() {
        Job job = anEnqueuedJob()
                .withAmountOfRetries(3)
                .withJobDetails(jobDetails()
                        .withClassName(TestService.class)
                        .withMethodName("doWork")
                        .withJobParameter(2))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job).hasAmountOfRetries(3);
    }

    @Test
    void labelsIsUsedIfProvidedByJobBuilder() {
        Job job = anEnqueuedJob()
                .withLabels("TestLabel", "Email")
                .withJobDetails(jobDetails()
                        .withClassName(TestService.class)
                        .withMethodName("doWork")
                        .withJobParameter(2))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job).hasLabels(Set.of("TestLabel", "Email"));
    }

    @Test
    void labelsIsUsedIfProvidedByAnnotation() {
        Job job = anEnqueuedJob()
                .withJobDetails(() -> testService.doWorkWithJobAnnotationAndLabels(3, "customer name"))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job).hasLabels(Set.of("label-3 - customer name"));
    }

    @Test
    void displayNameWithAnnotationUsingJobParametersAndMDCVariables() {
        MDC.put("customer.id", "1");
        Job job = anEnqueuedJob()
                .withoutName()
                .withJobDetails(jobDetails()
                        .withClassName(TestService.class)
                        .withMethodName("doWorkWithAnnotation")
                        .withJobParameter(5)
                        .withJobParameter("John Doe"))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job.getJobName()).isEqualTo("Doing some hard work for user John Doe (customerId: 1)");
    }

    @Test
    void displayNameWithAnnotationUsingJobParametersAndMDCVariablesThatDoNotExist() {
        MDC.put("key-not-used-in-annotation", "1");
        Job job = anEnqueuedJob()
                .withoutName()
                .withJobDetails(jobDetails()
                        .withClassName(TestService.class)
                        .withMethodName("doWorkWithAnnotation")
                        .withJobParameter(5)
                        .withJobParameter("John Doe"))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job.getJobName()).isEqualTo("Doing some hard work for user John Doe (customerId: (customer.id is not found in MDC))");
    }

    @Test
    void displayNameFromJobDetailsNormalMethod() {
        Job job = anEnqueuedJob()
                .withoutName()
                .withJobDetails(jobDetails()
                        .withClassName(TestService.class)
                        .withMethodName("doWork")
                        .withJobParameter(5.5))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job.getJobName()).isEqualTo("org.jobrunr.stubs.TestService.doWork(5.5)");
    }

    @Test
    void displayNameFromJobDetailsStaticMethod() {
        Job job = anEnqueuedJob()
                .withoutName()
                .withJobDetails(systemOutPrintLnJobDetails("some message"))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job.getJobName()).isEqualTo("java.lang.System.out.println(some message)");
    }

    @Test
    void displayNameFilterAlsoWorksWithJobContext() {
        Job job = anEnqueuedJob()
                .withoutName()
                .withJobDetails(jobDetails()
                        .withClassName(TestService.class)
                        .withMethodName("doWorkWithAnnotationAndJobContext")
                        .withJobParameter(5)
                        .withJobParameter("John Doe")
                        .withJobParameter(JobParameter.JobContext))
                .build();

        defaultJobFilter.onCreating(job);

        assertThat(job.getJobName()).isEqualTo("Doing some hard work for user John Doe with id 5");
    }
}