package com.example.demo.langchain4j;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

interface GeminiApi {

    @POST("api/generate")
    @Headers({"Content-Type: application/json"})
    Call<CompletionResponse> completion(@Body CompletionRequest completionRequest);

    @POST("api/generate")
    @Headers({"Content-Type: application/json"})
    @Streaming
    Call<ResponseBody> streamingCompletion(@Body CompletionRequest completionRequest);

    @POST("api/embed")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embed(@Body EmbeddingRequest embeddingRequest);

    @POST("api/chat")
    @Headers({"Content-Type: application/json"})
    Call<ChatResponse> chat(@Body ChatRequest chatRequest);

    @POST("/conversationgpt4-2")
    @Headers({"Content-Type: application/json"})
    @Streaming
    Call<ResponseBody> streamingChat(@Body ChatRequest chatRequest);

    @GET("api/tags")
    @Headers({"Content-Type: application/json"})
    Call<ModelsListResponse> listModels();

    @POST("api/show")
    @Headers({"Content-Type: application/json"})
    Call<GeminiModelCard> showInformation(@Body ShowModelInformationRequest modelDetailsRequest);

    @HTTP(method = "DELETE", path = "/api/delete", hasBody = true)
    @Headers({"Content-Type: application/json"})
    Call<Void> deleteModel(@Body DeleteModelRequest deleteModelRequest);
}