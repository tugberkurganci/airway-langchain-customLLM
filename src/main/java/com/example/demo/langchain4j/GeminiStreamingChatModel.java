package com.example.demo.langchain4j;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.InternalOpenAiHelper;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.example.demo.langchain4j.GeminiMessagesUtils.toGeminiMessages;
import static com.example.demo.langchain4j.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

public class GeminiStreamingChatModel implements StreamingChatLanguageModel {

    private final GeminiClient client;
    private final String modelName;
    private final Options options;
    private final String format;
    private final String baseUrl;



    @Builder
    public GeminiStreamingChatModel(String baseUrl,
                                    String modelName,
                                    Double temperature,
                                    Integer topK,
                                    Double topP,
                                    Double repeatPenalty,
                                    Integer seed,
                                    Integer numPredict,
                                    Integer numCtx,
                                    List<String> stop,
                                    String format,
                                    Duration timeout,
                                    Boolean logRequests,
                                    Boolean logResponses,
                                    Map<String, String> customHeaders,
                                    String apiKey, ChatMemoryProvider chatMemoryProvider, Object memoryId) {
        this.baseUrl = baseUrl;


        this.client = GeminiClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .customHeaders(customHeaders)
                .apiKey(apiKey)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.options = Options.builder()
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .repeatPenalty(repeatPenalty)
                .seed(seed)
                .numPredict(numPredict)
                .numCtx(numCtx)
                .stop(stop)
                .build();
        this.format = format;
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");



        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(toGeminiMessages(messages))
                .options(options)
                .format(format)
                .stream(true)
                .build();

        client.streamingChat(request, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        // Build the request with tools if necessary
        ChatRequest requestBuilder = ChatRequest.builder()
                .model(modelName)
                .messages(toGeminiMessages(messages))
                .options(options)
                .format(format)
                .stream(true).build();

         if (!Utils.isNullOrEmpty(toolSpecifications)) {
            // Handle a list of tools
            requestBuilder.setTools(GeminiMessagesUtils.toGeminiTools(toolSpecifications));
        }

        // Build the chat request


        // Send the request and handle streaming responses
        client.streamingChat(requestBuilder, handler);

    }



    public static GeminiStreamingChatModelBuilder builder() {
        return new GeminiStreamingChatModelBuilder();
    }

    public static class GeminiStreamingChatModelBuilder {
        public GeminiStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
