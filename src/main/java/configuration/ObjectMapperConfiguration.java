package configuration;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.gitlab4j.api.utils.JacksonJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.quarkus.jackson.ObjectMapperCustomizer;

public class ObjectMapperConfiguration {

	@Singleton
	ObjectMapper objectMapper(Instance<ObjectMapperCustomizer> customizers) {
		return new JacksonJson().getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	}
}
