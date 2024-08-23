package com.example.demo.langchain4j;


import com.example.demo.langchain4j.LangChain4jAssistant;
import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.service.TokenStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@RestController
@RequestMapping("/api1/chat")
public class ChatController {

    private final LangChain4jAssistant langChain4JAssistant;

    public ChatController(LangChain4jAssistant langChain4JAssistant) {
        this.langChain4JAssistant = langChain4JAssistant;
    }

    @PostMapping
    public Flux<String> chat(@RequestParam String chatId, @RequestParam String userMessage) throws JsonProcessingException {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        langChain4JAssistant.chat (chatId, userMessage)
                .onNext(sink::tryEmitNext)
                .onComplete(aiMessageResponse -> sink.tryEmitComplete())
                .onError(sink::tryEmitError)
                .start();

        return sink.asFlux();
    }
}
