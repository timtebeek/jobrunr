package org.jobrunr.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jobrunr.utils.StringUtils.*;

class StringUtilsTest {

    @Test
    void isNullOrEmpty() {
        assertThat(isNullOrEmpty(null)).isTrue();
        assertThat(isNullOrEmpty("")).isTrue();
        assertThat(isNullOrEmpty("bla")).isFalse();
    }

    @Test
    void isNotNullOrEmpty() {
        assertThat(isNotNullOrEmpty(null)).isFalse();
        assertThat(isNotNullOrEmpty("")).isFalse();
        assertThat(isNotNullOrEmpty("bla")).isTrue();
    }

    @Test
    void capitalize() {
        assertThat(StringUtils.capitalize("testMethod")).isEqualTo("TestMethod");
    }

    @Test
    void substringBeforeSplitterSingleChar() {
        assertThat(substringBefore("15", "-")).isEqualTo("15");
        assertThat(substringBefore("15-ea", "-")).isEqualTo("15");
    }

    @Test
    void substringBeforeSplitterMultiChar() {
        assertThat(substringBefore("this is a test", " is ")).isEqualTo("this");
        assertThat(substringBefore("this is a test", " was ")).isEqualTo("this is a test");
    }

    @Test
    void substringBeforeLast() {
        String input = "jar:file:/home/ronald/Projects/Personal/JobRunr/bugs/jobrunr_issue/target/demo-0.0.1-SNAPSHOT.jar!/BOOT-INF/lib/jobrunr-1.0.0-SNAPSHOT.jar!/org/jobrunr/storage/sql/common/migrations";
        assertThat(substringBeforeLast(input, "!")).isEqualTo("jar:file:/home/ronald/Projects/Personal/JobRunr/bugs/jobrunr_issue/target/demo-0.0.1-SNAPSHOT.jar!/BOOT-INF/lib/jobrunr-1.0.0-SNAPSHOT.jar");
    }

    @Test
    void substringAfterSplitterSingleChar() {
        assertThat(substringAfter("15", "-")).isNull();
        assertThat(substringAfter("15-ea", "-")).isEqualTo("ea");
    }

    @Test
    void substringAfterSplitterMultiChar() {
        assertThat(substringAfter("this is a test", " is ")).isEqualTo("a test");
        assertThat(substringAfter("this is a test", " was ")).isNull();
    }

    @Test
    void substringAfterLast() {
        String input = "jar:file:/home/ronald/Projects/Personal/JobRunr/bugs/jobrunr_issue/target/demo-0.0.1-SNAPSHOT.jar!/BOOT-INF/lib/jobrunr-1.0.0-SNAPSHOT.jar!/org/jobrunr/storage/sql/common/migrations";
        assertThat(substringAfterLast(input, "!")).isEqualTo("/org/jobrunr/storage/sql/common/migrations");
    }

    @Test
    void substringBetween() {
        assertThat(substringBetween("${some.string}", "${", "}")).isEqualTo("some.string");
        assertThat(substringBetween("some.string", "${", "}")).isNull();
    }

    @Test
    void lenientSubstringBetween() {
        assertThat(lenientSubstringBetween("open=some.string&close", "open=", "&close")).isEqualTo("some.string");
        assertThat(lenientSubstringBetween("open=some.string", "open=", "&close")).isEqualTo("some.string");
        assertThat(lenientSubstringBetween(null, "open=", "&close")).isNull();
    }
}