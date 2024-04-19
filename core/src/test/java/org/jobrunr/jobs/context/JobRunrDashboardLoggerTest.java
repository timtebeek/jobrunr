package org.jobrunr.jobs.context;

import org.assertj.core.api.Condition;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.context.JobDashboardLogger.JobDashboardLogLines;
import org.jobrunr.jobs.context.JobDashboardLogger.Level;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.utils.mapper.gson.GsonJsonMapper;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.jobrunr.utils.mapper.jsonb.JsonbJsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.not;
import static org.jobrunr.JobRunrAssertions.assertThat;
import static org.jobrunr.jobs.JobTestBuilder.aJobInProgress;
import static org.jobrunr.utils.SleepUtils.sleep;
import static org.jobrunr.utils.reflection.ReflectionUtils.cast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRunrDashboardLoggerTest {

    @Mock
    private Logger slfLogger;
    private JobRunrDashboardLogger jobRunrDashboardLogger;
    private final Marker marker = MarkerFactory.getMarker("some marker");

    @BeforeEach
    void setUpJobLogger() {
        jobRunrDashboardLogger = new JobRunrDashboardLogger(slfLogger);
    }

    @AfterEach
    void cleanUp() {
        JobRunrDashboardLogger.clearJob();
    }

    @Test
    void infoLoggingWithoutJob() {
        jobRunrDashboardLogger.info("simple message");

        verify(slfLogger).info("simple message");
    }

    @Test
    void infoLoggingWithJob() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.info("simple message");

        verify(slfLogger).info("simple message");
        assertThat(job).hasMetadata(InfoLog.withMessage("simple message"));
    }

    @Test
    void infoLoggingWithJobAndFormattingOneArgument() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.info("simple message {}", "hello");

        verify(slfLogger).info("simple message {}", "hello");
        assertThat(job).hasMetadata(InfoLog.withMessage("simple message hello"));
    }

    @Test
    void infoLoggingWithJobAndFormattingMultipleArguments() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.info("simple message {} {} {}", "hello", "again", "there");

        verify(slfLogger).info("simple message {} {} {}", "hello", "again", "there");
        assertThat(job).hasMetadata(InfoLog.withMessage("simple message hello again there"));
    }

    @Test
    void warnLoggingWithoutJob() {
        jobRunrDashboardLogger.warn("simple message");

        verify(slfLogger).warn("simple message");
    }

    @Test
    void warnLoggingWithJob() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.warn("simple message");

        verify(slfLogger).warn("simple message");
        assertThat(job).hasMetadata(WarnLog.withMessage("simple message"));
    }

    @Test
    void warnLoggingWithJobAndFormattingOneArgument() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.warn("simple message {}", "hello");

        verify(slfLogger).warn("simple message {}", "hello");
        assertThat(job).hasMetadata(WarnLog.withMessage("simple message hello"));
    }

    @Test
    void warnLoggingWithJobAndFormattingMultipleArguments() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.warn("simple message {} {} {}", "hello", "again", "there");

        verify(slfLogger).warn("simple message {} {} {}", "hello", "again", "there");
        assertThat(job).hasMetadata(WarnLog.withMessage("simple message hello again there"));
    }

    @Test
    void errorLoggingWithoutJob() {
        jobRunrDashboardLogger.error("simple message");

        verify(slfLogger).error("simple message");
    }

    @Test
    void errorLoggingWithJob() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.error("simple message");

        verify(slfLogger).error("simple message");
        assertThat(job).hasMetadata(ErrorLog.withMessage("simple message"));
    }

    @Test
    void errorLoggingWithJobAndFormattingOneArgument() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.error("simple message {}", "hello");

        verify(slfLogger).error("simple message {}", "hello");
        assertThat(job).hasMetadata(ErrorLog.withMessage("simple message hello"));
    }

    @Test
    void errorLoggingWithJobAndFormattingMultipleArguments() {
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.error("simple message {} {} {}", "hello", "again", "there");

        verify(slfLogger).error("simple message {} {} {}", "hello", "again", "there");
        assertThat(job).hasMetadata(ErrorLog.withMessage("simple message hello again there"));
    }

    @Test
    void infoLoggingWithJobAndThreshold() {
        jobRunrDashboardLogger = new JobRunrDashboardLogger(slfLogger, Level.WARN);
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.info("simple message");

        verify(slfLogger).info("simple message");
        assertThat(job).hasMetadata(not(InfoLog.withMessage("simple message")));
    }

    @Test
    void warnLoggingWithJobAndThreshold() {
        jobRunrDashboardLogger = new JobRunrDashboardLogger(slfLogger, Level.ERROR);
        final Job job = aJobInProgress().build();
        JobRunrDashboardLogger.setJob(job);

        jobRunrDashboardLogger.warn("simple message");

        verify(slfLogger).warn("simple message");
        assertThat(job).hasMetadata(not(WarnLog.withMessage("simple message")));
    }

    @Test
    void JobRunrDashboardLoggerIsThreadSafe1() throws InterruptedException {
        jobRunrDashboardLogger = new JobRunrDashboardLogger(slfLogger);

        final Job job1 = aJobInProgress().withName("job1").build();
        final Job job2 = aJobInProgress().withName("job2").build();

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final Thread thread1 = new Thread(loggingRunnable(job1, jobRunrDashboardLogger, countDownLatch));
        final Thread thread2 = new Thread(loggingRunnable(job2, jobRunrDashboardLogger, countDownLatch));

        thread1.start();
        thread2.start();

        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(job1)
                .hasMetadata(InfoLog.withMessage("info from job1"))
                .hasMetadata(not(InfoLog.withMessage("info from job2")));
        assertThat(job2)
                .hasMetadata(not(InfoLog.withMessage("info from job1")))
                .hasMetadata(InfoLog.withMessage("info from job2"));
    }

    @Test
    void name() {
        when(slfLogger.getName()).thenReturn("the name");
        assertThat(jobRunrDashboardLogger.getName()).isEqualTo("the name");
    }

    @Test
    void isTraceEnabled() {
        jobRunrDashboardLogger.isTraceEnabled();
        verify(slfLogger).isTraceEnabled();
    }

    @Test
    void trace() {
        jobRunrDashboardLogger.trace("trace");
        verify(slfLogger).trace("trace");
    }

    @Test
    void traceWithFormat() {
        jobRunrDashboardLogger.trace("trace with {}", "format");
        verify(slfLogger).trace("trace with {}", "format");
    }

    @Test
    void traceWithFormat2() {
        jobRunrDashboardLogger.trace("trace with {} {}", "format1", "format2");
        verify(slfLogger).trace("trace with {} {}", "format1", "format2");
    }

    @Test
    void traceWithFormat3() {
        jobRunrDashboardLogger.trace("trace with {} {} {}", "format1", "format2", "format3");
        verify(slfLogger).trace("trace with {} {} {}", "format1", "format2", "format3");
    }

    @Test
    void traceWithException() {
        Exception exception = new Exception();
        jobRunrDashboardLogger.trace("trace", exception);
        verify(slfLogger).trace("trace", exception);
    }

    @Test
    void markerTrace() {
        jobRunrDashboardLogger.trace(marker, "trace");
        verify(slfLogger).trace(marker, "trace");
    }

    @Test
    void markerTraceWithFormat() {
        jobRunrDashboardLogger.trace(marker, "trace with {}", "format");
        verify(slfLogger).trace(marker, "trace with {}", "format");
    }

    @Test
    void markerTraceWithFormat2() {
        jobRunrDashboardLogger.trace(marker, "trace with {} {}", "format1", "format2");
        verify(slfLogger).trace(marker, "trace with {} {}", "format1", "format2");
    }

    @Test
    void markerTraceWithFormat3() {
        jobRunrDashboardLogger.trace(marker, "trace with {} {} {}", "format1", "format2", "format3");
        verify(slfLogger).trace(marker, "trace with {} {} {}", "format1", "format2", "format3");
    }

    @Test
    void markerTraceWithException() {
        Exception exception = new Exception();
        jobRunrDashboardLogger.trace(marker, "trace", exception);
        verify(slfLogger).trace(marker, "trace", exception);
    }

    @Test
    void isDebugEnabled() {
        jobRunrDashboardLogger.isDebugEnabled();
        verify(slfLogger).isDebugEnabled();
    }

    @Test
    void debug() {
        jobRunrDashboardLogger.debug("Debug");
        verify(slfLogger).debug("Debug");
    }

    @Test
    void debugWithFormat() {
        jobRunrDashboardLogger.debug("Debug with {}", "format");
        verify(slfLogger).debug("Debug with {}", "format");
    }

    @Test
    void debugWithFormat2() {
        jobRunrDashboardLogger.debug("Debug with {} {}", "format1", "format2");
        verify(slfLogger).debug("Debug with {} {}", "format1", "format2");
    }

    @Test
    void debugWithFormat3() {
        jobRunrDashboardLogger.debug("Debug with {} {} {}", "format1", "format2", "format3");
        verify(slfLogger).debug("Debug with {} {} {}", "format1", "format2", "format3");
    }

    @Test
    void debugWithException() {
        Exception exception = new Exception();
        jobRunrDashboardLogger.debug("Debug", exception);
        verify(slfLogger).debug("Debug", exception);
    }

    @Test
    void markerDebug() {
        jobRunrDashboardLogger.debug(marker, "Debug");
        verify(slfLogger).debug(marker, "Debug");
    }

    @Test
    void markerDebugWithFormat() {
        jobRunrDashboardLogger.debug(marker, "Debug with {}", "format");
        verify(slfLogger).debug(marker, "Debug with {}", "format");
    }

    @Test
    void markerDebugWithFormat2() {
        jobRunrDashboardLogger.debug(marker, "Debug with {} {}", "format1", "format2");
        verify(slfLogger).debug(marker, "Debug with {} {}", "format1", "format2");
    }

    @Test
    void markerDebugWithFormat3() {
        jobRunrDashboardLogger.debug(marker, "Debug with {} {} {}", "format1", "format2", "format3");
        verify(slfLogger).debug(marker, "Debug with {} {} {}", "format1", "format2", "format3");
    }

    @Test
    void markerDebugWithException() {
        Exception exception = new Exception();
        jobRunrDashboardLogger.debug(marker, "Debug", exception);
        verify(slfLogger).debug(marker, "Debug", exception);
    }

    @Test
    void isInfoEnabled() {
        jobRunrDashboardLogger.isInfoEnabled();
        verify(slfLogger).isInfoEnabled();
    }

    @Test
    void isWarnEnabled() {
        jobRunrDashboardLogger.isWarnEnabled();
        verify(slfLogger).isWarnEnabled();
    }

    @Test
    void JobRunrDashboardLoggerIsThreadSafeUsingJackson() throws InterruptedException {
        JobRunrDashboardLoggerIsThreadSafeUsing(new JobMapper(new JacksonJsonMapper()));
    }

    @Test
    void JobRunrDashboardLoggerIsThreadSafeUsingGson() throws InterruptedException {
        JobRunrDashboardLoggerIsThreadSafeUsing(new JobMapper(new GsonJsonMapper()));
    }

    @Test
    void JobRunrDashboardLoggerIsThreadSafeUsingJsonB() throws InterruptedException {
        JobRunrDashboardLoggerIsThreadSafeUsing(new JobMapper(new JsonbJsonMapper()));
    }

    void JobRunrDashboardLoggerIsThreadSafeUsing(JobMapper jobMapper) throws InterruptedException {
        jobRunrDashboardLogger = new JobRunrDashboardLogger(slfLogger);

        final Job job = aJobInProgress().withName("job1").build();

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final Thread thread1 = new Thread(loggingRunnable(job, jobRunrDashboardLogger, countDownLatch));
        final Thread thread2 = new Thread(serializingRunnable(job, jobRunrDashboardLogger, jobMapper, countDownLatch));

        thread1.start();
        thread2.start();

        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(job)
                .hasMetadata(InfoLog.withMessage("info from job1"))
                .hasMetadata(InfoLog.withMessage("successfully serialized job job1 100 times"));
    }

    private Runnable loggingRunnable(Job job, JobRunrDashboardLogger logger, CountDownLatch countDownLatch) {
        return () -> {
            try {
                JobRunrDashboardLogger.setJob(job);
                for (int i = 0; i < 100; i++) {
                    logger.info("info from " + job.getJobName());
                    sleep(5);
                }
                countDownLatch.countDown();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private Runnable serializingRunnable(Job job, JobRunrDashboardLogger logger, JobMapper jobMapper, CountDownLatch countDownLatch) {
        return () -> {
            try {
                JobRunrDashboardLogger.setJob(job);
                for (int i = 0; i < 100; i++) {
                    jobMapper.serializeJob(job);
                    sleep(5);
                }
                logger.info("successfully serialized job " + job.getJobName() + " 100 times");
                countDownLatch.countDown();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static class InfoLog extends LogCondition {

        private InfoLog(String message) {
            super(Level.INFO, message);
        }

        public static InfoLog withMessage(String message) {
            return new InfoLog(message);
        }
    }

    private static class WarnLog extends LogCondition {

        private WarnLog(String message) {
            super(Level.WARN, message);
        }

        public static WarnLog withMessage(String message) {
            return new WarnLog(message);
        }
    }

    private static class ErrorLog extends LogCondition {

        private ErrorLog(String message) {
            super(Level.ERROR, message);
        }

        public static ErrorLog withMessage(String message) {
            return new ErrorLog(message);
        }
    }

    private static class LogCondition extends Condition {

        private final Level level;
        private final String message;

        protected LogCondition(Level level, String message) {
            this.level = level;
            this.message = message;
        }

        @Override
        public boolean matches(Object value) {
            Map<String, Object> metadata = cast(value);
            JobDashboardLogLines logLines = cast(metadata.get("jobRunrDashboardLog-2"));
            return logLines.getLogLines().stream().anyMatch(logLine -> level.equals(logLine.getLevel()) && message.equals(logLine.getLogMessage()));
        }
    }

}