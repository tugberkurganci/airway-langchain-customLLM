package com.example.demo.langchain4j;


import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.lang.Boolean.TRUE;

@Slf4j
class GeminiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(INDENT_OUTPUT);

    private final GeminiApi geminiApi;
    private final boolean logStreamingResponses;


    @Builder
    public GeminiClient(String baseUrl,
                        Duration timeout,
                        Boolean logRequests, Boolean logResponses, Boolean logStreamingResponses,
                        Map<String, String> customHeaders,
                        ChatMemoryProvider chatMemoryProvider
                        , String apiKey) {

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);
        if (logRequests != null && logRequests) {
            okHttpClientBuilder.addInterceptor(new GeminiRequestLoggingInterceptor());
        }
        if (logResponses != null && logResponses) {
            okHttpClientBuilder.addInterceptor(new GeminiResponseLoggingInterceptor());
        }
        this.logStreamingResponses = logStreamingResponses != null && logStreamingResponses;

        // add custom header interceptor
        if (customHeaders != null && !customHeaders.isEmpty()) {
            okHttpClientBuilder.addInterceptor(new GenericHeadersInterceptor(customHeaders));
        }

        // Add API key to the headers
        if (apiKey != null && !apiKey.isEmpty()) {
            okHttpClientBuilder.addInterceptor(new ApiKeyInterceptor(apiKey));
        }
        OkHttpClient okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();

        geminiApi = retrofit.create(GeminiApi.class);
    }

    public CompletionResponse completion(CompletionRequest request) {
        try {
            retrofit2.Response<CompletionResponse> retrofitResponse
                    = geminiApi.completion(request).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ChatResponse chat(ChatRequest request) {
        try {
            retrofit2.Response<ChatResponse> retrofitResponse
                    = geminiApi.chat(request).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void streamingCompletion(CompletionRequest request, StreamingResponseHandler<String> handler) {
        geminiApi.streamingCompletion(request).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> retrofitResponse) {
                try (InputStream inputStream = retrofitResponse.body().byteStream()) {
                    StringBuilder contentBuilder = new StringBuilder();


                    while (true) {
                        byte[] bytes = new byte[1024];
                        int len = inputStream.read(bytes);
                        String partialResponse = new String(bytes, 0, len);

                        if (logStreamingResponses) {
                            log.debug("Streaming partial response: {}", partialResponse);
                        }

                        CompletionResponse completionResponse = OBJECT_MAPPER.readValue(partialResponse, CompletionResponse.class);
                        contentBuilder.append(completionResponse.getResponse());
                        handler.onNext(completionResponse.getResponse());

                        if (TRUE.equals(completionResponse.getDone())) {
                            Response<String> response = Response.from(
                                    contentBuilder.toString(),
                                    new TokenUsage(
                                            completionResponse.getPromptEvalCount(),
                                            completionResponse.getEvalCount()
                                    )
                            );
                            handler.onComplete(response);
                            return;
                        }
                    }
                } catch (Exception e) {
                    handler.onError(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                handler.onError(throwable);
            }
        });
    }

    public void streamingChat(ChatRequest request, StreamingResponseHandler<AiMessage> handler) {
        geminiApi.streamingChat(request).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> retrofitResponse) {
                try (InputStream inputStream = retrofitResponse.body().byteStream()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        StringBuilder contentBuilder = new StringBuilder();
                        Long chatId = request.getChatId(); // Assuming you have a method to get chatId from the request

                        while (true) {
                            String partialResponse = reader.readLine();
                            if (logStreamingResponses) {
                                log.debug("Streaming partial response: {}", partialResponse);
                            }

                            ChatResponse chatResponse = OBJECT_MAPPER.readValue(partialResponse, ChatResponse.class);
                            String content = chatResponse.getResult();
                            contentBuilder.append(content);


                            handler.onNext(content);

                            if (TRUE.equals(chatResponse.getDone()) || true) {
                                Response<AiMessage> response = Response.from(
                                        AiMessage.from(contentBuilder.toString()),
                                        new TokenUsage(
                                                chatResponse.getPromptEvalCount(),
                                                chatResponse.getEvalCount()
                                        )
                                );
                                handler.onComplete(response);
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    handler.onError(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                handler.onError(throwable);
            }
        });
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        try {
            retrofit2.Response<EmbeddingResponse> retrofitResponse = geminiApi.embed(request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ModelsListResponse listModels() {
        try {
            retrofit2.Response<ModelsListResponse> retrofitResponse = geminiApi.listModels().execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public GeminiModelCard showInformation(ShowModelInformationRequest showInformationRequest) {
        try {
            retrofit2.Response<GeminiModelCard> retrofitResponse = geminiApi.showInformation(showInformationRequest).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Void deleteModel(DeleteModelRequest deleteModelRequest) {
        try {
            retrofit2.Response<Void> retrofitResponse = geminiApi.deleteModel(deleteModelRequest).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();

        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
    static class ApiKeyInterceptor implements Interceptor {
        private final String apiKey;

        ApiKeyInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }

        @NotNull
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request original = chain.request();
            Request request = original.newBuilder()
                    .header("x-rapidapi-key", apiKey)
                    .header("x-rapidapi-host", "chatgpt-42.p.rapidapi.com") // API anahtarını x-goog-api-key başlığına ekleyin
                    .build();
            return chain.proceed(request);
        }
    }
    static class GenericHeadersInterceptor implements Interceptor {

        private final Map<String, String> headers = new HashMap<>();

        GenericHeadersInterceptor(Map<String, String> headers) {
            Optional.ofNullable(headers)
                    .ifPresent(this.headers::putAll);
        }

        @NotNull
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request.Builder builder = chain.request().newBuilder();

            // Add headers
            this.headers.forEach(builder::addHeader);

            return chain.proceed(builder.build());
        }
    }
}
