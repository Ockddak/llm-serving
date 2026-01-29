package llm.serving.gateway.proxy.dto;

public record OllamaGenerateRequest(
        String model,
        String prompt,
        boolean stream
) {
}
