package com.sparsh.sentinel.copilot.chat;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CopilotChatService chatService;

    public ChatController(CopilotChatService chatService) {
        this.chatService = chatService;
    }

    /** Blocking ask. Simpler to test and to call from scripts; the UI uses the stream. */
    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody AskRequest request) {
        return chatService.ask(request.question());
    }

    /**
     * Streaming ask.
     *
     * <p>Citations are emitted as the first event, before any token, so the UI can render the
     * sources while the answer is still being written. Event names are part of the contract:
     * {@code citations}, then {@code token} repeatedly, then {@code done}.
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody AskRequest request) {
        CopilotChatService.Stream stream = chatService.stream(request.question());

        Flux<ServerSentEvent<Object>> citations = Flux.just(
                ServerSentEvent.builder()
                        .event("citations")
                        .data((Object) new CitationsEvent(stream.citations(), stream.grounded()))
                        .build());

        // The token is wrapped in JSON rather than sent as raw text. SSE says a parser should
        // strip one space after "data:", and servers are meant to add one - Spring does not.
        // Sending raw text therefore loses a token's own leading space, and " to" arrives as
        // "to", silently welding words together in the answer. JSON makes the payload exact.
        Flux<ServerSentEvent<Object>> tokens = stream.tokens()
                .map(token -> ServerSentEvent.builder()
                        .event("token")
                        .data((Object) new TokenEvent(token))
                        .build());

        Flux<ServerSentEvent<Object>> done = Flux.just(
                ServerSentEvent.builder()
                        .event("done")
                        .data((Object) "")
                        .build());

        return citations.concatWith(tokens).concatWith(done);
    }

    record CitationsEvent(List<Citation> citations, boolean groundedInSources) {
    }

    /** One chunk of the answer. JSON-wrapped so whitespace survives SSE framing exactly. */
    record TokenEvent(String text) {
    }
}
