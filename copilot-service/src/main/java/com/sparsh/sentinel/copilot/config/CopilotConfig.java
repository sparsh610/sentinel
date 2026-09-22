package com.sparsh.sentinel.copilot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CopilotProperties.class)
public class CopilotConfig {

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * Splits an extracted document into chunks small enough to embed meaningfully.
     *
     * <p>{@code keepSeparator = true} matters more than it looks: policy documents are full of
     * numbered clauses, and stripping the separators makes a citation impossible to trace back
     * to the paragraph a human would point at.
     */
    @Bean
    TokenTextSplitter tokenTextSplitter(CopilotProperties properties) {
        return TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSizeTokens())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
    }
}
