package com.example.demo.langchain4j;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.example.demo.langchain4j.GeminiMessagesUtils.*;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.time.Duration.ofSeconds;

public class GeminiChatModel implements ChatLanguageModel {

    private final GeminiClient client;
    private final String modelName;
    private final Options options;
    private final String format;
    private final Integer maxRetries;

    @Builder
    public GeminiChatModel(String baseUrl,
                           String apiKey,
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
                           Integer maxRetries,
                           Map<String, String> customHeaders,
                           Boolean logRequests,
                           Boolean logResponses) {
        this.client = GeminiClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .customHeaders(customHeaders)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(logResponses)
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
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        ChatRequest request = ChatRequest.builder()
                .model(modelName)

                .messages(toGeminiMessages(messages))
                .options(options)
                .format(format)
                .stream(false)
                .build();

        ChatResponse response = withRetry(() -> client.chat(request), maxRetries);

        return Response.from(
                AiMessage.from(response.getContent().getContent()),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount())
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");

        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(toGeminiMessages(messages))
                .options(options)
                .format(format)
                .stream(false)
                .build();

        ChatResponse response = withRetry(() -> client.chat(request), maxRetries);

        return Response.from(
                response.getContent().getToolCalls() != null ?
                        AiMessage.from(toToolExecutionRequest(response.getContent().getToolCalls())) :
                        AiMessage.from(response.getContent().getContent()),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount())
        );
    }

    public static GeminiChatModelBuilder builder() {
        return new GeminiChatModelBuilder();
    }

    public static class GeminiChatModelBuilder {
        public GeminiChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }

}
