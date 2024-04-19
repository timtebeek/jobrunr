package org.jobrunr.dashboard.server.http.url;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchUrlTest {

    @Test
    void noMatch() {
        boolean matches = new MatchUrl("/api").matches("/dashboard");
        assertThat(matches).isFalse();
    }

    @Test
    void match() {
        boolean matches = new MatchUrl("/api/jobs").matches("/api/jobs");
        assertThat(matches).isTrue();
    }

    @Test
    void noMatchWithParamsAndDifferentSize() {
        boolean matches = new MatchUrl("/api/jobs/enqueued/test").matches("/api/jobs/:state/test/extra");
        assertThat(matches).isFalse();
    }

    @Test
    void matchWithParams() {
        boolean matches = new MatchUrl("/api/jobs/enqueued/test").matches("/api/jobs/:state/test");
        assertThat(matches).isTrue();
    }

    @Test
    void noMatchWithParams() {
        boolean matches = new MatchUrl("/api/jobs/enqueued/wrong").matches("/api/jobs/:state/test");
        assertThat(matches).isFalse();
    }

    @Test
    void toRequestUrl() {
        RequestUrl requestUrl = new MatchUrl("/api/jobs/enqueued/test").toRequestUrl("/api/jobs/:state/test");
        assertThat(requestUrl.param(":state")).isEqualTo("enqueued");
    }

    @Test
    void toRequestUrlWithQueryParams() {
        RequestUrl requestUrl = new MatchUrl("/api/jobs/enqueued?offset=2").toRequestUrl("/api/jobs/:state");
        assertThat(requestUrl.param(":state")).isEqualTo("enqueued");
    }
}