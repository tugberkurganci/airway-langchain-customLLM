package com.example.demo.langchain4j;


import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ResourceLoader;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

@Configuration
public class LangChain4jConfig {

    @Autowired
    @Lazy
    ChatMemoryProvider chatMemoryProvider;


    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    // In the real world, ingesting documents would often happen separately, on a CI server or similar
    @Bean
    CommandLineRunner ingestDocsForLangChain(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            Tokenizer tokenizer, // Tokenizer is provided by langchain4j-open-ai-spring-boot-starter
            ResourceLoader resourceLoader
    ) {
        return args -> {
            var resource = resourceLoader.getResource("classpath:terms-of-service.txt");
            var termsOfUse = loadDocument(resource.getFile().toPath(), new TextDocumentParser());
            var ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(recursive(50, 0, tokenizer))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(termsOfUse);
        };
    }

    @Bean
    ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel
    ) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.6)
                .build();
    }

    @Bean
    ChatMemoryProvider chatMemoryProvider(Tokenizer tokenizer) {
        // Tokenizer is provided by langchain4j-open-ai-spring-boot-starter
        return chatId -> TokenWindowChatMemory.withMaxTokens(1000, tokenizer);
    }
    @Bean
    public LangChain4jAssistant langChain4jAssistant() {

            StreamingChatLanguageModel model = GeminiStreamingChatModel.builder()
                    .baseUrl("https://chatgpt-42.p.rapidapi.com/")
                    .apiKey("00d3e4670bmsh09842bddf35e7c1p1f8af9jsn620b4bdae9c6")
                    .modelName("gemini-1.5-pro")/// Replace with the correct model name if needed
                    .chatMemoryProvider(chatMemoryProvider)
                    .build();
            return  AiServices.builder(LangChain4jAssistant.class).streamingChatLanguageModel(model).tools(new LangChainTools()).chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10)).build();



    }
}

