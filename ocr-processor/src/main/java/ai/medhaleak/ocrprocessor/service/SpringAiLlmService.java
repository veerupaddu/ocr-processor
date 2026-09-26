package ai.medhaleak.ocrprocessor.service;

import ai.medhaleak.ocrprocessor.config.AppProperties;
import ai.medhaleak.ocrprocessor.exception.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmService.class);

    private final ChatClient chatClient;
    private final AppProperties props;

    public SpringAiLlmService(
            @Qualifier("deepSeekChatClient") ChatClient chatClient,
            AppProperties props) {
        this.chatClient = chatClient;
        this.props = props;
    }

    @Override
    public String summarise(String text) throws LlmUnavailableException {
        if (text == null || text.isBlank()) return "";

        int maxChars = props.llm().maxInputChars();
        String truncated = text.length() > maxChars ? text.substring(0, maxChars) : text;
        String prompt = props.llm().promptTemplate().replace("{extractedText}", truncated);

        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM summarisation failed", e);
            throw new LlmUnavailableException("LLM service unavailable", e);
        }
    }
}
