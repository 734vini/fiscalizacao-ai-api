package com.fiscalizacao.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalizacao.ai.dto.OccurrenceDto.*;
import com.fiscalizacao.ai.model.OccurrenceEvaluation;
import com.fiscalizacao.ai.model.OccurrenceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Serviço de IA usando Google Gemini (gratuito).
 * Chave gratuita em: https://aistudio.google.com/app/apikey
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnthropicAiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.0-flash}")
    private String model;

    private static final String SYSTEM_PROMPT = """
            Você é o sistema oficial de resposta automatizada da Prefeitura Municipal.
            Sua função é receber o STATUS ATUAL de uma ocorrência urbana e determinar
            qual deve ser o PRÓXIMO STATUS, gerando também uma mensagem institucional
            personalizada e específica para aquela ocorrência.

            Progressão natural dos status:
              Aberto → Em analise → Em execucao → Finalizado

            Diretrizes:
            - Avance exatamente um passo na progressão acima
            - Se o status já for "Finalizado", mantenha-o e informe que a ocorrência está encerrada
            - Considere o histórico anterior para dar coesão e continuidade às respostas
            - Tom formal mas acessível, como comunicação oficial de prefeitura
            - A mensagem DEVE mencionar especificamente o tipo de problema e/ou localização informados
            - Máximo 2 frases, sem repetir frases genéricas
            - Nunca use frases como "Agradecemos a colaboração do cidadão" ou similares
            - Responda APENAS com JSON válido, sem blocos de código markdown, sem texto antes ou depois:
              {"nextStatus": "STATUS_AQUI", "message": "Mensagem específica da prefeitura aqui."}

            Os únicos valores válidos para nextStatus são (use exatamente como escrito):
              "Aberto", "Em analise", "Em execucao", "Finalizado"
            """;

    public AiEvaluationResult evaluate(EvaluationRequest request, List<OccurrenceEvaluation> history) {
        String fullPrompt = SYSTEM_PROMPT + "\n\n" + buildUserPrompt(request, history);

        // Gemini API request body
        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", fullPrompt)))
            ),
            "generationConfig", Map.of(
                "temperature", 0.4,
                "maxOutputTokens", 8192
            )
        );

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + apiKey;

            String rawResponse = webClient.post()
                    .uri(url)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseGeminiResponse(rawResponse, request.getStatus());

        } catch (Exception e) {
            log.error("Erro ao chamar API Gemini: {}", e.getMessage(), e);
            return fallback(request.getStatus());
        }
    }

    private String buildUserPrompt(EvaluationRequest request, List<OccurrenceEvaluation> history) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== DADOS DA OCORRÊNCIA ===\n");
        sb.append("ID: ").append(request.getOccurrenceId()).append("\n");
        sb.append("Título: ").append(request.getTitle()).append("\n");
        sb.append("Categoria: ").append(request.getCategory()).append("\n");

        if (request.getDescription() != null)
            sb.append("Descrição: ").append(request.getDescription()).append("\n");

        if (request.getPriority() != null)
            sb.append("Prioridade: ").append(request.getPriority()).append("/5\n");

        if (request.getLocation() != null) {
            var loc = request.getLocation();
            sb.append("Localização: ");
            if (loc.getStreet() != null) sb.append(loc.getStreet());
            if (loc.getNumber() != null) sb.append(", ").append(loc.getNumber());
            if (loc.getCity() != null)   sb.append(" - ").append(loc.getCity());
            if (loc.getState() != null)  sb.append("/").append(loc.getState());
            sb.append("\n");
        }

        sb.append("\n=== STATUS ATUAL ===\n");
        sb.append(request.getStatus()).append("\n");
        sb.append("Determine o PRÓXIMO status adequado e gere a mensagem institucional.\n");

        if (!history.isEmpty()) {
            sb.append("\n=== HISTÓRICO DE AVALIAÇÕES ANTERIORES ===\n");
            history.forEach(h -> sb.append(String.format(
                    "- [%s] %s → %s: \"%s\"\n",
                    h.getEvaluatedAt().toString().substring(0, 16),
                    h.getPreviousStatus() != null ? h.getPreviousStatus() : "—",
                    h.getFinalStatus(),
                    h.getAiMessage()
            )));
        } else {
            sb.append("\n[Esta é a PRIMEIRA avaliação desta ocorrência]\n");
        }

        return sb.toString();
    }

    private AiEvaluationResult parseGeminiResponse(String rawResponse, String currentStatus) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            // Gemini: candidates[0].content.parts[0].text
            String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            // Remove possíveis markdown fences
            text = text.replaceAll("```json|```", "").trim();

            // Extrai o JSON da resposta
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }

            JsonNode parsed = objectMapper.readTree(text);
            String nextStatus = parsed.has("nextStatus") ? parsed.get("nextStatus").asText() : null;
            String message    = parsed.has("message")    ? parsed.get("message").asText()
                    : "Ocorrência avaliada pela Prefeitura Municipal.";

            if (nextStatus == null || !isValidStatus(nextStatus)) {
                log.warn("Gemini retornou nextStatus inválido '{}', usando progressão natural", nextStatus);
                nextStatus = naturalNext(currentStatus);
            }

            return AiEvaluationResult.builder()
                    .finalStatus(nextStatus)
                    .message(message)
                    .build();

        } catch (Exception e) {
            log.error("Erro ao parsear resposta do Gemini: {}", e.getMessage());
            return fallback(currentStatus);
        }
    }

    private AiEvaluationResult fallback(String currentStatus) {
        return AiEvaluationResult.builder()
                .finalStatus(naturalNext(currentStatus))
                .message("Sua ocorrência foi recebida e está sendo processada pela Prefeitura Municipal. Agradecemos a colaboração do cidadão.")
                .build();
    }

    private boolean isValidStatus(String label) {
        try {
            OccurrenceStatus.fromLabel(label);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String naturalNext(String currentLabel) {
        try {
            return OccurrenceStatus.fromLabel(currentLabel).next().getLabel();
        } catch (IllegalArgumentException e) {
            return OccurrenceStatus.EM_ANALISE.getLabel();
        }
    }
}
