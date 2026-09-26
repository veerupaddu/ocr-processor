package ai.medhaleak.ocrprocessor;

import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = ChatClientAutoConfiguration.class)
@ConfigurationPropertiesScan
public class OcrProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(OcrProcessorApplication.class, args);
    }
}
