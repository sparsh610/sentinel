package com.sparsh.sentinel.copilot.agent.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * Tool results as JSON with ISO-8601 dates. Spring AI's default writes an {@code Instant} as epoch
 * seconds, which a model reads as a meaningless number; "2026-09-30T09:53:08Z" it can reason about.
 */
public class IsoDateResultConverter implements ToolCallResultConverter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String convert(Object result, Type returnType) {
        try {
            return JSON.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the tool result", e);
        }
    }
}
