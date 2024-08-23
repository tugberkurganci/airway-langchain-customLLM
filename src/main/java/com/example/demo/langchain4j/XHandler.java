package com.example.demo.langchain4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServiceContext;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.function.Consumer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Handles response from a language model for AI Service that is streamed token-by-token.
 * Handles both regular (text) responses and responses with the request to execute one or multiple tools.
 */

class AAHandler implements StreamingResponseHandler<AiMessage> {


    private final AiServiceContext context;


    private final Object memoryId;

    private final Consumer<String> tokenHandler;
    private final Consumer<Response<AiMessage>> completionHandler;
    private final Consumer<Throwable> errorHandler;

    private final TokenUsage tokenUsage;

    AAHandler    (AiServiceContext context,
                                      Object memoryId,
                                      Consumer<String> tokenHandler,
                                      Consumer<Response<AiMessage>> completionHandler,
                                      Consumer<Throwable> errorHandler,
                                      TokenUsage tokenUsage) {
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");

        this.tokenHandler = ensureNotNull(tokenHandler, "tokenHandler");
        this.completionHandler = completionHandler;
        this.errorHandler = errorHandler;

        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");
    }


    public Object getMemoryId() {
        return memoryId;
    }

    @Override
    public void onNext(String token) {
        tokenHandler.accept(token);
    }

    @Override
    public void onComplete(Response<AiMessage> response) {

        AiMessage aiMessage = response.content();

        if (context.hasChatMemory()) {
            context.chatMemory(memoryId).add(aiMessage);
        }

        if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                ToolExecutor toolExecutor = context.toolExecutors.get(toolExecutionRequest.name());
                String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                        toolExecutionRequest,
                        toolExecutionResult
                );
                context.chatMemory(memoryId).add(toolExecutionResultMessage);
            }

            context.streamingChatModel.generate(
                    context.chatMemory(memoryId).messages(),
                    context.toolSpecifications,
                    new AAHandler(
                            context,
                            memoryId,
                            tokenHandler,
                            completionHandler,
                            errorHandler,
                            tokenUsage.add(response.tokenUsage())
                    )
            );
        } else {
            if (completionHandler != null) {
                completionHandler.accept(Response.from(
                        aiMessage,
                        tokenUsage.add(response.tokenUsage()),
                        response.finishReason())
                );
            }
        }
    }

    @Override
    public void onError(Throwable error) {
        if (errorHandler != null) {
            try {
                errorHandler.accept(error);
            } catch (Exception e) {

            }
        } else {

        }
    }
}
