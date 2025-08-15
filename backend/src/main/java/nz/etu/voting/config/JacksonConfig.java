package nz.etu.voting.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        // Configure Stream Write Constraints to handle deep nesting
        StreamWriteConstraints constraints = StreamWriteConstraints.builder()
                .maxNestingDepth(2000)  // Increase from default 1000
                .build();
        mapper.getFactory().setStreamWriteConstraints(constraints);

        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
        return mapper;
    }
}