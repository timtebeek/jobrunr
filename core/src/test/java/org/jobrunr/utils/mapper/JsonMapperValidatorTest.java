package org.jobrunr.utils.mapper;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.bind.JsonbConfig;
import org.jobrunr.utils.mapper.gson.GsonJsonMapper;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.jobrunr.utils.mapper.jsonb.JsonbJsonMapper;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jobrunr.utils.mapper.JsonMapperValidator.validateJsonMapper;

class JsonMapperValidatorTest {

    @Test
    void invalidJacksonJsonMapperNoJavaTimeModule() {
        assertThatThrownBy(() -> validateJsonMapper(new InvalidJacksonJsonMapper(new ObjectMapper().registerModule(new Jdk8Module()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The JsonMapper you provided cannot be used as it deserializes jobs in an incorrect way.");
        //.hasRootCauseMessage("Java 8 date/time type `java.time.Instant` not supported by default: add Module \"com.fasterxml.jackson.datatype:jackson-datatype-jsr310\" to enable handling (through reference chain: org.jobrunr.jobs.Job[\"jobStates\"]->java.util.Collections$UnmodifiableRandomAccessList[0]->org.jobrunr.jobs.states.ProcessingState[\"createdAt\"])");
    }

    @Test
    void invalidJacksonJsonMapperNoISO8601TimeFormat() {
        assertThatThrownBy(() -> validateJsonMapper(new InvalidJacksonJsonMapper(new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The JsonMapper you provided cannot be used as it deserializes jobs in an incorrect way.")
                .hasRootCauseMessage("Timestamps are wrongly formatted for JobRunr. They should be in ISO8601 format.");
    }

    @Test
    void invalidJacksonJsonMapperPropertiesInsteadOfFields() {
        assertThatThrownBy(() -> validateJsonMapper(new InvalidJacksonJsonMapper(new ObjectMapper()
                        .registerModule(new Jdk8Module())
                        .registerModule(new JavaTimeModule())
                        .setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"))
                ))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The JsonMapper you provided cannot be used as it deserializes jobs in an incorrect way.")
                .hasRootCauseMessage("Job Serialization should use fields and not getters/setters.");
    }
    
    @Test
    void invalidJacksonJsonMapperNoPolymorphism() {
        assertThatThrownBy(() -> validateJsonMapper(new InvalidJacksonJsonMapper(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"))
                        .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                ))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The JsonMapper you provided cannot be used as it deserializes jobs in an incorrect way.")
                .hasRootCauseMessage("Polymorphism is not supported as no @class annotation is present with fully qualified name of the different Job states.");
    }

    @Test
    void invalidGsonJsonMapper() {
        assertThatThrownBy(() -> validateJsonMapper(new InvalidGsonJsonMapper(new GsonBuilder().create()
                ))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The JsonMapper you provided cannot be used as it deserializes jobs in an incorrect way.");
    }

    @Test
    void invalidJsonbJsonMapper() {
        assertThatThrownBy(() -> validateJsonMapper(new InvalidJsonbJsonMapper(new JsonbConfig()))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The JsonMapper you provided cannot be used as it deserializes jobs in an incorrect way.");
    }

    @Test
    void validJacksonJsonMapper() {
        assertThatCode(() -> validateJsonMapper(new JacksonJsonMapper())).doesNotThrowAnyException();
    }

    @Test
    void validGsonJsonMapper() {
        assertThatCode(() -> validateJsonMapper(new GsonJsonMapper())).doesNotThrowAnyException();
    }

    @Test
    void validJsonBJsonMapper() {
        assertThatCode(() -> validateJsonMapper(new JsonbJsonMapper())).doesNotThrowAnyException();
    }

    public class InvalidJacksonJsonMapper extends JacksonJsonMapper {

        public InvalidJacksonJsonMapper(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        protected ObjectMapper initObjectMapper(ObjectMapper objectMapper, boolean moduleAutoDiscover) {
            return objectMapper;
        }
    }

    public class InvalidGsonJsonMapper extends GsonJsonMapper {

        public InvalidGsonJsonMapper(Gson gson) {
            super(gson);
        }

        @Override
        protected Gson initGson(GsonBuilder gsonBuilder) {
            return gsonBuilder.create();
        }
    }

    public class InvalidJsonbJsonMapper extends JsonbJsonMapper {

        public InvalidJsonbJsonMapper(JsonbConfig config) {
            super(config);
        }

        @Override
        protected JsonbConfig initJsonbConfig(JsonbConfig jsonbConfig) {
            return jsonbConfig;
        }
    }
}
