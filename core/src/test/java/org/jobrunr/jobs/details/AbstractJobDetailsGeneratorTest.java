package org.jobrunr.jobs.details;

import io.github.artsok.RepeatedIfExceptionsTest;
import org.assertj.core.api.Assertions;
import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.JobParameter;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.jobs.lambdas.IocJobLambdaFromStream;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.jobs.lambdas.JobLambdaFromStream;
import org.jobrunr.stubs.TaskEvent;
import org.jobrunr.stubs.TestService;
import org.jobrunr.stubs.TestServiceInterface;
import org.jobrunr.utils.annotations.Because;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.util.Textifier;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.jobrunr.JobRunrAssertions.assertThat;
import static org.jobrunr.jobs.details.JobDetailsGeneratorUtils.toFQResource;
import static org.jobrunr.stubs.TestService.Task.PROGRAMMING;
import static org.jobrunr.utils.SleepUtils.sleep;
import static org.jobrunr.utils.StringUtils.substringAfterLast;

public abstract class AbstractJobDetailsGeneratorTest {

    private TestService testService;
    private TestServiceInterface testServiceInterface;
    private JobDetailsGenerator jobDetailsGenerator;

    private enum SomeEnum {
        Value1,
        Value2
    }

    @BeforeEach
    void setUp() {
        jobDetailsGenerator = getJobDetailsGenerator();
        testService = new TestService();
        testServiceInterface = testService;
    }

    protected abstract JobDetailsGenerator getJobDetailsGenerator();

    protected JobDetails toJobDetails(JobLambda job) {
        return jobDetailsGenerator.toJobDetails(job);
    }

    protected <T> JobDetails toJobDetails(T itemFromStream, JobLambdaFromStream<T> jobLambda) {
        return jobDetailsGenerator.toJobDetails(itemFromStream, jobLambda);
    }

    protected JobDetails toJobDetails(IocJobLambda<TestService> iocJobLambda) {
        return jobDetailsGenerator.toJobDetails(iocJobLambda);
    }

    @Test
    @Disabled("for debugging")
    void logByteCode() {
        String name = AbstractJobDetailsGeneratorTest.class.getName();
        String location = new File(".").getAbsolutePath() + "/build/classes/java/test/" + toFQResource(name) + ".class";

        //String location = "/Users/rdehuyss/Projects/Personal/jobrunr/jobrunr/language-support/jobrunr-kotlin-16-support/build/classes/kotlin/test/org/jobrunr/scheduling/JobSchedulerTest.class";
        assertThatCode(() -> Textifier.main(new String[]{location})).doesNotThrowAnyException();
    }

    @Test
    void jobLambdaCallingSystemOutPrintln() {
        JobLambda job = () -> System.out.println("This is a test!");
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(System.class)
                .hasStaticFieldName("out")
                .hasMethodName("println")
                .hasArgs("This is a test!");
    }

    @Test
    void jobLambdaCallingInlineSystemOutPrintln() {
        JobDetails jobDetails = toJobDetails((JobLambda) () -> System.out.println("This is a test!"));
        assertThat(jobDetails)
                .hasClass(System.class)
                .hasStaticFieldName("out")
                .hasMethodName("println")
                .hasArgs("This is a test!");
    }

    @Test
    void jobLambdaContainingTwoStaticJobsShouldFailWithNiceException() {
        final JobLambda jobLambda = () -> {
            System.out.println("This is a test!");
            System.out.println("This is a test!");
        };
        assertThatThrownBy(() -> toJobDetails(jobLambda))
                .isInstanceOf(JobRunrException.class)
                .hasMessage("JobRunr only supports enqueueing/scheduling of one method");
    }

    @Test
    void jobLambdaContainingTwoJobsShouldFailWithNiceException() {
        final JobLambda jobLambda = () -> {
            testService.doWork();
            testService.doWork();
        };
        assertThatThrownBy(() -> toJobDetails(jobLambda))
                .isInstanceOf(JobRunrException.class)
                .hasMessage("JobRunr only supports enqueueing/scheduling of one method");
    }

    @Test
    void jobLambdaCallingStaticMethod() {
        UUID id = UUID.randomUUID();
        JobLambda job = () -> TestService.doWorkInStaticMethod(id);
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWorkInStaticMethod")
                .hasArgs(id);
    }

    @Test
    void jobLambdaCallingInlineStaticMethod() {
        UUID id = UUID.randomUUID();
        JobDetails jobDetails = toJobDetails(() -> TestService.doWorkInStaticMethod(id));
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWorkInStaticMethod")
                .hasArgs(id);
    }

    @Test
    void jobLambdaCallMethodReference() {
        JobLambda job = testService::doWork;
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasNoArgs();
    }

    @Test
    void jobLambdaCallInstanceMethodNull() {
        TestService.Work work = null;
        JobLambda job = () -> testService.doWork(work);
        assertThatThrownBy(() -> toJobDetails(job))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("You are passing null as a parameter to your background job for type org.jobrunr.stubs.TestService$Work - JobRunr prevents this to fail fast.");
    }

    @Test
    void jobLambdaCallInstanceMethodOtherLambda() {
        Supplier<Boolean> supplier = () -> {
            System.out.println("Dit is een test");
            return true;
        };
        JobLambda job = () -> {
            if (supplier.get()) {
                System.out.println("In nested lambda");
            }
        };
        assertThatThrownBy(() -> toJobDetails(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You are passing another (nested) Java 8 lambda to JobRunr - this is not supported. Try to convert your lambda to a class or a method.");
    }

    @Test
    void jobLambdaCallInstanceMethodBIPUSH() {
        JobLambda job = () -> testService.doWork(5);
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(5);
    }

    @Test
    void jobLambdaCallInstanceMethodSIPUSH() {
        JobLambda job = () -> testService.doWorkThatTakesLong(500);
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWorkThatTakesLong")
                .hasArgs(500);
    }

    @Test
    void jobLambdaCallInstanceMethodLCONST() {
        JobLambda job = () -> testService.doWorkThatTakesLong(1L);
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWorkThatTakesLong")
                .hasArgs(1L);
    }

    @Test
    void inlineJobLambdaCallInstanceMethod() {
        JobDetails jobDetails = toJobDetails((JobLambda) () -> testService.doWork());
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasNoArgs();
    }

    @Test
    void jobLambdaWithIntegerAndJobContext() {
        JobLambda job = () -> testService.doWork(3, JobContext.Null);
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(3, JobContext.Null);
    }

    @Test
    void jobLambdaWithDouble() {
        JobLambda job = () -> testService.doWork(3.3);
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(3.3);
    }

    @Test
    void jobLambdaWithSum() {
        int a = 6;
        int b = 3;
        JobLambda job = () -> testService.doWork(a + b);

        assertThatCode(() -> toJobDetails(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasRootCauseInstanceOf(UnsupportedOperationException.class)
                .hasRootCauseMessage("You are summing two numbers while enqueueing/scheduling jobs - for performance reasons it is better to do the calculation outside of the job lambda");
    }

    @Test
    void jobLambdaWithSubtraction() {
        int a = 6;
        int b = 3;
        JobLambda job = () -> testService.doWork(a - b);

        assertThatCode(() -> toJobDetails(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasRootCauseInstanceOf(UnsupportedOperationException.class)
                .hasRootCauseMessage("You are subtracting two numbers while enqueueing/scheduling jobs - for performance reasons it is better to do the calculation outside of the job lambda");
    }

    @Test
    void jobLambdaWithMultiplication() {
        int a = 6;
        int b = 3;
        JobLambda job = () -> testService.doWork(a * b);

        assertThatCode(() -> toJobDetails(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasRootCauseInstanceOf(UnsupportedOperationException.class)
                .hasRootCauseMessage("You are multiplying two numbers while enqueueing/scheduling jobs - for performance reasons it is better to do the calculation outside of the job lambda");
    }

    @Test
    void jobLambdaWithDivision() {
        int a = 6;
        int b = 3;
        JobLambda job = () -> testService.doWork(a / b);

        assertThatCode(() -> toJobDetails(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasRootCauseInstanceOf(UnsupportedOperationException.class)
                .hasRootCauseMessage("You are dividing two numbers while enqueueing/scheduling jobs - for performance reasons it is better to do the calculation outside of the job lambda");
    }

    @Test
    void jobLambdaWithMultipleInts() {
        JobLambda job = () -> testService.doWork(3, 97693);
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(3, 97693);
    }

    @Test
    void jobLambdaWithMultipleParameters() {
        for (int i = 0; i < 3; i++) {
            int finalI = i;
            Instant now = Instant.now();
            JobLambda job = () -> testService.doWork("some string", finalI, now);

            JobDetails jobDetails = toJobDetails(job);
            assertThat(jobDetails)
                    .hasClass(TestService.class)
                    .hasMethodName("doWork")
                    .hasArgs("some string", finalI, now);
        }
    }

    @Test
    void jobLambdaWithObject() {
        for (int i = 0; i < 3; i++) {
            int finalI = i;
            JobLambda job = () -> testService.doWork(new TestService.Work(finalI, "a String", UUID.randomUUID()));

            JobDetails jobDetails = toJobDetails(job);
            assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWork");
            JobParameter jobParameter = jobDetails.getJobParameters().get(0);
            Assertions.assertThat(jobParameter.getClassName()).isEqualTo(TestService.Work.class.getName());
            Assertions.assertThat(jobParameter.getObject())
                    .isInstanceOf(TestService.Work.class)
                    .hasFieldOrPropertyWithValue("workCount", finalI)
                    .hasFieldOrPropertyWithValue("someString", "a String")
                    .hasFieldOrProperty("uuid");
        }
    }

    @Test
    void jobLambdaWithFile() {
        JobLambda job = () -> testService.doWorkWithFile(new File("/tmp/file.txt"));

        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWorkWithFile");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(File.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(File.class)
                .isEqualTo(new File("/tmp/file.txt"));
    }

    @Test
    void jobLambdaWithPath() {
        Path path = Paths.get("/tmp/file.txt");
        JobLambda job = () -> testService.doWorkWithPath(path);

        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWorkWithPath");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(Path.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(Path.class)
                .isEqualTo(path);
    }

    @Test
    void jobLambdaWithPathsGetInLambda() {
        JobLambda job = () -> testService.doWorkWithPath(Paths.get("/tmp/file.txt"));

        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWorkWithPath");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(Path.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(Path.class)
                .isEqualTo(Paths.get("/tmp/file.txt"));
    }

    @Test
    void jobLambdaWithPaths() {
        for (int i = 0; i < 3; i++) {
            final Path path = Paths.get("/tmp/file" + i + ".txt");
            JobLambda job = () -> testService.doWorkWithPath(path);

            JobDetails jobDetails = toJobDetails(job);
            assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWorkWithPath");
            JobParameter jobParameter = jobDetails.getJobParameters().get(0);
            Assertions.assertThat(jobParameter.getClassName()).isEqualTo(Path.class.getName());
            Assertions.assertThat(jobParameter.getObject())
                    .isInstanceOf(Path.class)
                    .isEqualTo(path);
        }
    }

    @Test
    void jobLambdaWithPathsGetMultiplePartsInLambda() {
        JobLambda job = () -> testService.doWorkWithPath(Paths.get("/tmp", "folder", "subfolder", "file.txt"));

        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWorkWithPath");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(Path.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(Path.class)
                .isEqualTo(Paths.get("/tmp/folder/subfolder/file.txt"));
    }

    @Test
    void jobLambdaWithPathOfInLambda() {
        JobLambda job = () -> testService.doWorkWithPath(Paths.get("/tmp/file.txt"));

        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWorkWithPath");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(Path.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(Path.class)
                .isEqualTo(Paths.get("/tmp/file.txt"));
    }

    @Test
    void jobLambdaWithObjectCreatedOutsideOfLambda() {
        for (int i = 0; i < 3; i++) {
            TestService.Work work = new TestService.Work(i, "a String", UUID.randomUUID());
            JobLambda job = () -> testService.doWork(work);

            JobDetails jobDetails = toJobDetails(job);
            assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWork");
            JobParameter jobParameter = jobDetails.getJobParameters().get(0);
            Assertions.assertThat(jobParameter.getClassName()).isEqualTo(TestService.Work.class.getName());
            Assertions.assertThat(jobParameter.getObject())
                    .isInstanceOf(TestService.Work.class)
                    .hasFieldOrPropertyWithValue("workCount", i)
                    .hasFieldOrPropertyWithValue("someString", "a String")
                    .hasFieldOrPropertyWithValue("uuid", work.getUuid());
        }
    }

    @Test
    void jobLambdaWithSupportedPrimitiveTypes() {
        JobLambda job = () -> testService.doWork(true, 3, 5L, 3.3F, 2.3D);
        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(true, 3, 5L, 3.3F, 2.3D);
    }

    @Test
    void jobLambdaWithUnsupportedPrimitiveTypes() {
        JobLambda job = () -> testService.doWork((byte) 0x3, (short) 2, 'c');
        assertThatThrownBy(() -> toJobDetails(job))
                .isInstanceOf(JobRunrException.class)
                .hasMessage("Error parsing lambda")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jobLambdaCallingMultiLine() {
        final List<UUID> workStream = getWorkStream().collect(toList());
        Instant now = Instant.now();
        for (UUID id : workStream) {
            JobLambda job = () -> {
                int someInt = 6;
                testService.doWork(id, someInt, now);
            };
            JobDetails jobDetails = toJobDetails(job);
            assertThat(jobDetails)
                    .hasClass(TestService.class)
                    .hasMethodName("doWork")
                    .hasArgs(id, 6, now);
        }
    }

    @Test
    void jobLambdaCallingMultiLineStatementSystemOutPrintln() {
        final List<UUID> workStream = getWorkStream().collect(toList());
        LocalDateTime now = LocalDateTime.now();
        for (UUID id : workStream) {
            JobLambda job = () -> {
                UUID testId = id;
                int someInt = 6;
                double someDouble = 5.3;
                float someFloat = 5.3F;
                long someLong = 3L;
                boolean someBoolean = true;
                SomeEnum someEnum = SomeEnum.Value1;
                System.out.println("This is a test: " + testId + "; " + someInt + "; " + someDouble + "; " + someFloat + "; " + someLong + "; " + someBoolean + "; " + someEnum + "; " + now);
            };
            JobDetails jobDetails = toJobDetails(job);
            assertThat(jobDetails)
                    .hasClass(System.class)
                    .hasStaticFieldName("out")
                    .hasMethodName("println")
                    .hasArg(obj -> obj.toString().startsWith("This is a test: " + id)
                            && obj.toString().contains(" 6; 5.3; 5.3; 3; true; Value1;")
                            && obj.toString().contains(now.toString()));
        }
    }

    @Test
    void jobLambdaCallingMultiLineStatementThatLoadsFromAService() {
        JobLambda job = () -> {
            UUID testId = testService.getAnUUID();
            testService.doWork(testId);
        };

        JobDetails jobDetails = toJobDetails(job);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArg(obj -> obj instanceof UUID);
    }

    @Test
    void jobLambdaWithArgumentThatIsNotUsed() {
        final Stream<Integer> range = IntStream.range(0, 1).boxed();
        assertThatCode(() -> jobDetailsGenerator.toJobDetails(range, (i) -> testService.doWork())).doesNotThrowAnyException();
    }

    @Test
    void jobLambdaWithStaticMethodInLambda() {
        JobLambda jobLambda = () -> testService.doWork(TestService.Work.from(2, "a String", UUID.randomUUID()));
        final JobDetails jobDetails = toJobDetails(jobLambda);

        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWork");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(TestService.Work.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(TestService.Work.class)
                .hasFieldOrPropertyWithValue("workCount", 2)
                .hasFieldOrPropertyWithValue("someString", "a String")
                .hasFieldOrProperty("uuid");
    }

    @Test
    void jobLambdaWhichReturnsSomething() {
        JobLambda jobLambda = () -> testService.doWorkAndReturnResult("someString");
        JobDetails jobDetails = toJobDetails(jobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWorkAndReturnResult")
                .hasArgs("someString");
    }

    @Test
    void simpleJobLambdaWithStream() {
        List<UUID> workStream = getWorkStream().collect(toList());
        final JobLambdaFromStream<UUID> lambda = (uuid) -> testService.doWork(uuid, 3, now());
        final List<JobDetails> allJobDetails = workStream.stream()
                .map(x -> jobDetailsGenerator.toJobDetails(x, lambda))
                .collect(toList());

        Assertions.assertThat(allJobDetails).hasSize(5);
        assertThat(allJobDetails.get(0)).hasClass(TestService.class).hasMethodName("doWork");
        Assertions.assertThat(allJobDetails.get(0).getJobParameters().get(0).getObject()).isEqualTo(workStream.get(0));
        Assertions.assertThat(allJobDetails.get(4).getJobParameters().get(0).getObject()).isEqualTo(workStream.get(4));
    }

    @Test
    void jobLambdaWithStream() {
        Stream<UUID> workStream = getWorkStream();
        AtomicInteger atomicInteger = new AtomicInteger();
        final JobLambdaFromStream<UUID> lambda = (uuid) -> testService.doWork(uuid.toString(), atomicInteger.incrementAndGet(), now());
        final List<JobDetails> allJobDetails = workStream
                .map(x -> jobDetailsGenerator.toJobDetails(x, lambda))
                .collect(toList());

        Assertions.assertThat(allJobDetails).hasSize(5);
        assertThat(allJobDetails.get(0)).hasClass(TestService.class).hasMethodName("doWork");
        Assertions.assertThat(allJobDetails.get(0).getJobParameters().get(1).getObject()).isEqualTo(1);
        Assertions.assertThat(allJobDetails.get(4).getJobParameters().get(1).getObject()).isEqualTo(5);
    }

    @Test
    void jobLambdaWithStreamAndObject() {
        List<UUID> workStream = getWorkStream().collect(toUnmodifiableList());
        AtomicInteger atomicInteger = new AtomicInteger();
        final JobLambdaFromStream<TestService.Work> lambda = (work) -> testService.doWork(work);
        final List<JobDetails> allJobDetails = workStream.stream()
                .map(uuid -> new TestService.Work(atomicInteger.getAndIncrement(), Integer.toString(atomicInteger.get()), uuid))
                .map(x -> jobDetailsGenerator.toJobDetails(x, lambda))
                .collect(toList());

        Assertions.assertThat(allJobDetails).hasSize(5);
        assertThat(allJobDetails.get(0)).hasClass(TestService.class).hasMethodName("doWork");
        Assertions.assertThat(allJobDetails.get(0).getJobParameters().get(0).getObject())
                .isInstanceOf(TestService.Work.class)
                .hasFieldOrPropertyWithValue("someString", "1")
                .hasFieldOrPropertyWithValue("uuid", workStream.get(0));
        Assertions.assertThat(allJobDetails.get(4).getJobParameters().get(0).getObject())
                .isInstanceOf(TestService.Work.class)
                .hasFieldOrPropertyWithValue("someString", "5")
                .hasFieldOrPropertyWithValue("uuid", workStream.get(4));
    }

    @Test
    void jobLambdaWithStreamAndMethodReference() {
        final UUID uuid = UUID.randomUUID();
        final JobDetails jobDetails = jobDetailsGenerator.toJobDetails(uuid, TestService::doWorkWithUUID);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWorkWithUUID")
                .hasArgs(uuid);
    }

    @Test
    void jobLambdaWithStreamAndMethodReferenceInSameFile() {
        final UUID uuid = UUID.randomUUID();
        final JobDetails jobDetails = jobDetailsGenerator.toJobDetails(uuid, AbstractJobDetailsGeneratorTest::doWorkWithUUID);
        assertThat(jobDetails)
                .hasClass(AbstractJobDetailsGeneratorTest.class)
                .hasMethodName("doWorkWithUUID")
                .hasArgs(uuid);
    }

    @Test
    void iocJobLambda() {
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork();
        JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasNoArgs();
    }

    @Test
    void inlineIocJobLambda() {
        JobDetails jobDetails = jobDetailsGenerator.toJobDetails((IocJobLambda<TestService>) (x) -> x.doWork(5));
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(5);
    }

    @Test
    void iocJobLambdaWithIntegerAndJobContext() {
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork(3, JobContext.Null);
        JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(3, JobContext.Null);
    }

    @Test
    void iocJobLambdaWithDouble() {
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork(3.3);
        JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(3.3);
    }

    @Test
    void iocJobLambdaWithMultipleInts() {
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork(3, 97693);
        JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(3, 97693);
    }

    @Test
    void iocJobLambdaWithMultipleParameters() {
        for (int i = 0; i < 3; i++) {
            int finalI = i;
            Instant now = Instant.now();
            IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork("some string", finalI, now);

            JobDetails jobDetails = toJobDetails(iocJobLambda);
            assertThat(jobDetails)
                    .hasClass(TestService.class)
                    .hasMethodName("doWork")
                    .hasArgs("some string", finalI, now);
        }
    }

    @Test
    void iocJobLambdaWithObject() {
        for (int i = 0; i < 3; i++) {
            int finalI = i;
            IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork(new TestService.Work(finalI, "a String", UUID.randomUUID()));

            JobDetails jobDetails = toJobDetails(iocJobLambda);
            assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWork");
            JobParameter jobParameter = jobDetails.getJobParameters().get(0);
            Assertions.assertThat(jobParameter.getClassName()).isEqualTo(TestService.Work.class.getName());
            Assertions.assertThat(jobParameter.getObject())
                    .isInstanceOf(TestService.Work.class)
                    .hasFieldOrPropertyWithValue("workCount", finalI)
                    .hasFieldOrPropertyWithValue("someString", "a String")
                    .hasFieldOrProperty("uuid");
        }
    }

    @Test
    void ioCJobLambdaWithFile() {
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWorkWithFile(new File("/tmp/file.txt"));

        JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWorkWithFile");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(File.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(File.class)
                .isEqualTo(new File("/tmp/file.txt"));
    }

    @Test
    void ioCJobLambdaWithPath() {
        Path path = Paths.get("/tmp/file.txt");
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWorkWithPath(path);

        JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWorkWithPath");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(Path.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(Path.class)
                .isEqualTo(path);
    }

    @Test
    void iocJobLambdaWithObjectCreatedOutsideOfLambda() {
        for (int i = 0; i < 3; i++) {
            TestService.Work work = new TestService.Work(i, "a String", UUID.randomUUID());
            IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork(work);

            JobDetails jobDetails = toJobDetails(iocJobLambda);
            assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWork");
            JobParameter jobParameter = jobDetails.getJobParameters().get(0);
            Assertions.assertThat(jobParameter.getClassName()).isEqualTo(TestService.Work.class.getName());
            Assertions.assertThat(jobParameter.getObject())
                    .isInstanceOf(TestService.Work.class)
                    .hasFieldOrPropertyWithValue("workCount", i)
                    .hasFieldOrPropertyWithValue("someString", "a String")
                    .hasFieldOrProperty("uuid");
        }
    }

    @Test
    void iocJobLambdaWithSupportedPrimitiveTypes() {
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork(true, 3, 5L, 3.3F, 2.3D);
        JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(true, 3, 5L, 3.3F, 2.3D);
    }

    @Test
    void iocJobLambdaWithSupportedPrimitiveTypesLOAD() {
        for (int i = 0; i < 3; i++) {
            final boolean finalB = i % 2 == 0;
            final int finalI = i;
            final long finalL = 5L + i;
            final float finalF = 3.3F + i;
            final double finalD = 2.3D + i;
            IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork(finalB, finalI, finalL, finalF, finalD);
            JobDetails jobDetails = toJobDetails(iocJobLambda);
            assertThat(jobDetails)
                    .hasClass(TestService.class)
                    .hasMethodName("doWork")
                    .hasArgs(i % 2 == 0, finalI, 5L + i, 3.3F + i, 2.3D + i);
        }
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/158")
    void iocJobLambdaWithPrimitiveWrappersLOAD() {
        for (int i = 0; i < 3; i++) {
            final Boolean finalB = i % 2 == 0;
            final Integer finalI = i;
            final Long finalL = 5L + i;
            final Float finalF = 3.3F + i;
            final Double finalD = 2.3D + i;
            IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork(finalB, finalI, finalL, finalF, finalD);
            JobDetails jobDetails = toJobDetails(iocJobLambda);
            assertThat(jobDetails)
                    .hasClass(TestService.class)
                    .hasMethodName("doWork")
                    .hasArgs(i % 2 == 0, finalI, 5L + i, 3.3F + i, 2.3D + i);
        }
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/165")
    void jobLambdaWithPrimitiveParametersAndWrappersInMethodLOAD() {
        long id = 1L;
        long env = 2L;
        String param = "test";

        final JobDetails jobDetails = toJobDetails(() -> testService.jobRunBatchWrappers(id, env, param, TestUtils.getCurrentLogin()));
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("jobRunBatchWrappers")
                .hasArgs(1L, 2L, "test", "Some string");
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/165")
    void ioCJobLambdaWithPrimitiveParametersAndWrappersInMethodLOAD() {
        long id = 1L;
        long env = 2L;
        String param = "test";

        IocJobLambda<TestService> iocJobLambda = (x) -> x.jobRunBatchWrappers(id, env, param, TestUtils.getCurrentLogin());
        final JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("jobRunBatchWrappers")
                .hasArgs(1L, 2L, "test", "Some string");
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/165")
    void jobLambdaWithPrimitiveParametersAndPrimitivesInMethodLOAD() {
        long id = 1L;
        long env = 2L;
        String param = "test";

        final JobDetails jobDetails = toJobDetails(() -> testService.jobRunBatchPrimitives(id, env, param, TestUtils.getCurrentLogin()));
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("jobRunBatchPrimitives")
                .hasArgs(1L, 2L, "test", "Some string");
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/165")
    void ioCJobLambdaWithPrimitiveParametersAndPrimitivesInMethodLOAD() {
        long id = 1L;
        long env = 2L;
        String param = "test";

        IocJobLambda<TestService> iocJobLambda = (x) -> x.jobRunBatchPrimitives(id, env, param, TestUtils.getCurrentLogin());
        final JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("jobRunBatchPrimitives")
                .hasArgs(1L, 2L, "test", "Some string");
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/165")
    void jobLambdaWithCombinationParametersAndPrimitivesInMethodLOAD() {
        long id = 1L;
        Long env = 2L;
        String param = "test";

        final JobDetails jobDetails = toJobDetails(() -> testService.jobRunBatchPrimitives(id, env, param, TestUtils.getCurrentLogin()));
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("jobRunBatchPrimitives")
                .hasArgs(1L, 2L, "test", "Some string");
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/165")
    void ioCJobLambdaWithCombinationParametersAndPrimitivesInMethodLOAD() {
        long id = 1L;
        Long env = 2L;
        String param = "test";

        IocJobLambda<TestService> iocJobLambda = (x) -> x.jobRunBatchPrimitives(id, env, param, TestUtils.getCurrentLogin());
        final JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("jobRunBatchPrimitives")
                .hasArgs(1L, 2L, "test", "Some string");
    }


    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/165")
    void jobLambdaWithCombinationParametersAndWrappersInMethodLOAD() {
        long id = 1L;
        Long env = 2L;
        String param = "test";

        final JobDetails jobDetails = toJobDetails(() -> testService.jobRunBatchWrappers(id, env, param, TestUtils.getCurrentLogin()));
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("jobRunBatchWrappers")
                .hasArgs(1L, 2L, "test", "Some string");
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/165")
    void ioCJobLambdaWithCombinationParametersAndWrappersInMethodLOAD() {
        long id = 1L;
        Long env = 2L;
        String param = "test";

        IocJobLambda<TestService> iocJobLambda = (x) -> x.jobRunBatchPrimitives(id, env, param, TestUtils.getCurrentLogin());
        final JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("jobRunBatchPrimitives")
                .hasArgs(1L, 2L, "test", "Some string");
    }

    @Test
    void iocJobLambdaWithUnsupportedPrimitiveTypes() {
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWork((byte) 0x3, (short) 2, 'c');
        assertThatThrownBy(() -> toJobDetails(iocJobLambda))
                .isInstanceOf(JobRunrException.class)
                .hasMessage("Error parsing lambda")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iocJobLambdaWithArgumentThatIsNotUsed() {
        IocJobLambdaFromStream<TestService, Integer> iocJobLambdaFromStream = (x, i) -> x.doWork();
        assertThatCode(() -> jobDetailsGenerator.toJobDetails(5, iocJobLambdaFromStream)).doesNotThrowAnyException();
    }

    @Test
    void iocJobLambdaWhichReturnsSomething() {
        IocJobLambda<TestService> iocJobLambda = (x) -> x.doWorkAndReturnResult("someString");
        JobDetails jobDetails = toJobDetails(iocJobLambda);
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWorkAndReturnResult")
                .hasArgs("someString");
    }

    @Test
    void simpleIoCJobLambdaWithStream() {
        List<UUID> workStream = getWorkStream().collect(toList());
        final IocJobLambdaFromStream<TestService, UUID> lambda = (service, uuid) -> service.doWork(uuid, 5, now());
        final List<JobDetails> allJobDetails = workStream.stream()
                .map(x -> jobDetailsGenerator.toJobDetails(x, lambda))
                .collect(toList());

        Assertions.assertThat(allJobDetails).hasSize(5);
        assertThat(allJobDetails.get(0)).hasClass(TestService.class).hasMethodName("doWork");
        Assertions.assertThat(allJobDetails.get(0).getJobParameters().get(0).getObject()).isEqualTo(workStream.get(0));
        Assertions.assertThat(allJobDetails.get(4).getJobParameters().get(0).getObject()).isEqualTo(workStream.get(4));
    }

    @Test
    void ioCJobLambdaWithStream() {
        Stream<UUID> workStream = getWorkStream();
        AtomicInteger atomicInteger = new AtomicInteger();
        final IocJobLambdaFromStream<TestService, UUID> lambda = (service, uuid) -> service.doWork(uuid.toString(), atomicInteger.incrementAndGet(), now());
        final List<JobDetails> allJobDetails = workStream
                .map(x -> jobDetailsGenerator.toJobDetails(x, lambda))
                .collect(toList());

        Assertions.assertThat(allJobDetails).hasSize(5);
        assertThat(allJobDetails.get(0)).hasClass(TestService.class).hasMethodName("doWork");
        Assertions.assertThat(allJobDetails.get(0).getJobParameters().get(1).getObject()).isEqualTo(1);
        Assertions.assertThat(allJobDetails.get(4).getJobParameters().get(1).getObject()).isEqualTo(5);
    }

    @Test
    void ioCJobWithStreamAndObject() {
        List<UUID> workStream = getWorkStream().collect(toUnmodifiableList());
        AtomicInteger atomicInteger = new AtomicInteger();
        final IocJobLambdaFromStream<TestService, TestService.Work> lambda = (service, work) -> service.doWork(work);
        final List<JobDetails> allJobDetails = workStream.stream()
                .map(uuid -> new TestService.Work(atomicInteger.getAndIncrement(), Integer.toString(atomicInteger.get()), uuid))
                .map(x -> jobDetailsGenerator.toJobDetails(x, lambda))
                .collect(toList());

        Assertions.assertThat(allJobDetails).hasSize(5);
        assertThat(allJobDetails.get(0)).hasClass(TestService.class).hasMethodName("doWork");
        Assertions.assertThat(allJobDetails.get(0).getJobParameters().get(0).getObject())
                .isInstanceOf(TestService.Work.class)
                .hasFieldOrPropertyWithValue("someString", "1")
                .hasFieldOrPropertyWithValue("uuid", workStream.get(0));
        Assertions.assertThat(allJobDetails.get(4).getJobParameters().get(0).getObject())
                .isInstanceOf(TestService.Work.class)
                .hasFieldOrPropertyWithValue("someString", "5")
                .hasFieldOrPropertyWithValue("uuid", workStream.get(4));
    }

    @Test
    void ioCJobLambdaWithStaticMethodInLambda() {
        IocJobLambda<TestService> jobLambda = x -> x.doWork(TestService.Work.from(2, "a String", UUID.randomUUID()));
        final JobDetails jobDetails = toJobDetails(jobLambda);

        assertThat(jobDetails).hasClass(TestService.class).hasMethodName("doWork");
        JobParameter jobParameter = jobDetails.getJobParameters().get(0);
        Assertions.assertThat(jobParameter.getClassName()).isEqualTo(TestService.Work.class.getName());
        Assertions.assertThat(jobParameter.getObject())
                .isInstanceOf(TestService.Work.class)
                .hasFieldOrPropertyWithValue("workCount", 2)
                .hasFieldOrPropertyWithValue("someString", "a String")
                .hasFieldOrProperty("uuid");
    }

    @Test
    void inlineJobLambdaFromInterface() {
        JobDetails jobDetails = toJobDetails((JobLambda) () -> testServiceInterface.doWork());
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasNoArgs();
    }

    @Test
    void methodReferenceJobLambdaFromInterface() {
        JobDetails jobDetails = toJobDetails((JobLambda) testServiceInterface::doWork);
        assertThat(jobDetails)
                .hasClass(TestServiceInterface.class)
                .hasMethodName("doWork")
                .hasNoArgs();
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/375")
    void jobLambdaWithEnum() {
        JobDetails jobDetails = toJobDetails(() -> testService.doWorkWithEnum(PROGRAMMING));
        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWorkWithEnum")
                .hasArgs(PROGRAMMING);
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/335")
    void jobLambdaWithDifferentParametersCalledFromOtherMethod() {
        UUID uuid1 = UUID.randomUUID();
        assertThat(createJobDetails(uuid1))
                .hasClass(TestService.GithubIssue335.class)
                .hasMethodName("run")
                .hasArgs(uuid1);

        UUID uuid2 = UUID.randomUUID();
        assertThat(createJobDetails(uuid2))
                .hasClass(TestService.GithubIssue335.class)
                .hasMethodName("run")
                .hasArgs(uuid2);
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/335")
    void jobDetailsGeneratorIsThreadSafe() {
        UUID uuid1 = UUID.randomUUID();
        assertThat(createJobDetails(uuid1))
                .hasClass(TestService.GithubIssue335.class)
                .hasMethodName("run")
                .hasArgs(uuid1);

        UUID uuid2 = UUID.randomUUID();
        assertThat(createJobDetails(uuid2))
                .hasClass(TestService.GithubIssue335.class)
                .hasMethodName("run")
                .hasArgs(uuid2);
    }

    @RepeatedIfExceptionsTest(repeats = 3)
    @Because("https://github.com/jobrunr/jobrunr/issues/456")
    void createJobDetailsInMultipleThreads() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(4);
        final Map<String, JobDetails> jobDetailsResults = new ConcurrentHashMap<>();
        final Thread thread1 = new Thread(createJobDetailsRunnable(countDownLatch, "thread1", jobDetailsResults));
        final Thread thread2 = new Thread(createJobDetailsRunnable(countDownLatch, "thread2", jobDetailsResults));
        final Thread thread3 = new Thread(createJobDetailsRunnable(countDownLatch, "thread3", jobDetailsResults));
        final Thread thread4 = new Thread(createJobDetailsRunnable(countDownLatch, "thread4", jobDetailsResults));

        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();

        await().untilAsserted(() -> assertThat(jobDetailsResults).hasSize(2000));
        jobDetailsResults.keySet().stream()
                .forEach(key -> {
                    Integer givenInput = parseInt(substringAfterLast(key, "-"));
                    assertThat(jobDetailsResults.get(key)).hasArgs(givenInput);
                });
    }

    @Test
    @Because("https://stackoverflow.com/questions/74161840/enqueue-jobrunr-background-job-with-lambda-subclass-of-abstract-class")
    void withSubClass() {
        TaskEvent taskEvent = new TaskEvent();
        taskEvent.tasks.add(new TaskEvent.Task1());
        taskEvent.tasks.add(new TaskEvent.Task2());

        JobDetails jobDetails = toJobDetails((JobLambda) () -> taskEvent.tasks.get(0).process("id1"));
        assertThat(jobDetails.getClassName()).isEqualTo(TaskEvent.Task1.class.getName());
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/694")
    void cachingOfPrimitiveIntValues() {
        JobDetails jobDetails1 = createJobLambdaWithPrimitiveInt(1);
        assertThat(jobDetails1)
                .hasMethodName("runItInt")
                .hasArgs(1);

        JobDetails jobDetails2 = createJobLambdaWithPrimitiveInt(2);
        assertThat(jobDetails2)
                .hasMethodName("runItInt")
                .hasArgs(2);
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/694")
    void castingOfPrimitiveIntValues() {
        JobDetails jobDetails = createJobLambdaWithPrimitiveIntCastedToPrimitiveLong(3);
        assertThat(jobDetails)
                .hasMethodName("runItLong")
                .hasArgs(3L);
    }

    @Test
    @Because("https://github.com/jobrunr/jobrunr/issues/694")
    void passingObject() {
        TestService.Work work1 = new TestService.Work(2, "a", UUID.randomUUID());
        JobDetails jobDetails1 = createJobLambdaWithObject(work1);
        assertThat(jobDetails1)
                .hasMethodName("runItWithObject")
                .hasArgs(work1);

        TestService.Work work2 = new TestService.Work(3, "b", UUID.randomUUID());
        JobDetails jobDetails2 = createJobLambdaWithObject(work2);
        assertThat(jobDetails2)
                .hasMethodName("runItWithObject")
                .hasArgs(work2);
    }

    @Test
    void streamWithMethodInvocationInLambda() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        toJobDetails(id1, (id) -> testService.doWork(id.toString()));
        JobDetails jobDetails = toJobDetails(id2, (id) -> testService.doWork(id.toString()));

        assertThat(jobDetails)
                .hasClass(TestService.class)
                .hasMethodName("doWork")
                .hasArgs(id2.toString());
    }

    @Test
    void createJobWithProxyClassImplementingInterfaceRespectsInterface() {
        TestServiceInterface testServiceProxy = (TestServiceInterface) Proxy.newProxyInstance(TestServiceInterface.class.getClassLoader(), new Class[]{TestServiceInterface.class}, new DummyProxyInvocationHandler());
        JobDetails jobDetails = toJobDetails(() -> testServiceProxy.doWork());

        assertThat(jobDetails)
                .hasClass(TestServiceInterface.class)
                .hasMethodName("doWork")
                .hasNoArgs();
    }

    @Test
    void createJobWithSyntheticClassImplementingInterfaceRespectsInterface() {
        TestServiceInterface testService = () -> System.out.println("A Java 8 lambda is a synthetic class...");
        assertThat(testService.getClass()).matches(Class::isSynthetic);

        JobDetails jobDetails = toJobDetails(() -> testService.doWork());
        assertThat(jobDetails)
                .hasClass(TestServiceInterface.class)
                .hasMethodName("doWork")
                .hasNoArgs();
    }

    private Runnable createJobDetailsRunnable(CountDownLatch countDownLatch, String threadNbr, Map<String, JobDetails> jobDetailsResults) {
        Random random = new Random();
        return () -> {
            try {
                for (int i = 0; i < 500; i++) {
                    Integer input = random.nextInt(1000);
                    JobDetails jobDetails = toJobDetails(() -> testService.doWork(input));
                    jobDetailsResults.put(threadNbr + "-" + i + "-" + input, jobDetails);
                    sleep(1);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                countDownLatch.countDown();
            }
        };
    }


    // must be kept in separate method for test of Github Issue 335
    private JobDetails createJobDetails(UUID uuid) {
        return toJobDetails(() -> new TestService.GithubIssue335().run(uuid));
    }

    private Stream<UUID> getWorkStream() {
        return IntStream.range(0, 5)
                .mapToObj(i -> UUID.randomUUID());
    }

    public void doWorkWithUUID(UUID uuid) {
        System.out.println("Doing some work... " + uuid);
    }

    public static class TestUtils {

        public static String getCurrentLogin() {
            return "Some string";
        }
    }

    public JobDetails createJobLambdaWithPrimitiveInt(int intValue) {
        return toJobDetails(() -> runItInt(intValue));
    }

    public JobDetails createJobLambdaWithPrimitiveIntCastedToPrimitiveLong(int intValue) {
        return toJobDetails(() -> runItLong(intValue));
    }

    public JobDetails createJobLambdaWithObject(TestService.Work work) {
        return toJobDetails(() -> runItWithObject(work));
    }

    public static void runItInt(int intValue) {
        System.out.println("runItInt, intValue:" + intValue);
    }

    public static void runItLong(long longValue) {
        System.out.println("runItInt, longValue:" + longValue);
    }

    public static void runItWithObject(TestService.Work work) {
        System.out.println("runItWithObject, work:" + work.getUuid());
    }

    private static class DummyProxyInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            System.out.println("Invoking method " + method.getName() + " on object " + proxy);
            return null;
        }
    }

}
