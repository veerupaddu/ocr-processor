package ai.medhaleak.ocrprocessor.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class AiClientConfig {

    /**
     * JDK HttpURLConnection throws HttpRetryException when an API responds
     * while the request body is still streaming, which hides the real status.
     * java.net.http reads that response normally.
     */
    @Bean
    RestClientCustomizer llmRestClientCustomizer() {
        return builder -> builder.requestFactory(new JdkClientHttpRequestFactory());
    }

    /** DeepSeek ChatClient — used by LlmService for text summarisation. */
    @Bean
    @Qualifier("deepSeekChatClient")
    public ChatClient deepSeekChatClient(
            @Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel) {
        return ChatClient.create(deepSeekChatModel);
    }
}
