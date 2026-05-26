package com.fiscalizacao.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class OccurrenceDto {

    // ---- REQUEST ----

    /**
     * Payload recebido do app-fiscalizacao-urbana (Next.js).
     *
     * Campos ignorados intencionalmente (não agregam valor à avaliação da IA):
     *   - reporter (name, email)
     *   - imageUrl
     *   - recurrenceCount
     *   - commentCount
     *   - createdAt / updatedAt
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true) // ignora reporter, imageUrl, recurrenceCount, commentCount, createdAt, updatedAt
    public static class EvaluationRequest {

        @NotNull(message = "O ID da ocorrência é obrigatório")
        private Integer occurrenceId;

        @NotBlank(message = "O título é obrigatório")
        private String title;

        private String description;

        @NotBlank(message = "A categoria é obrigatória")
        private String category;

        /**
         * Status ATUAL da ocorrência.
         * A IA decide qual será o próximo com base nele + contexto.
         * Valores válidos: "Aberto", "Em analise", "Em execucao", "Finalizado"
         */
        @NotBlank(message = "O status é obrigatório")
        private String status;

        private Integer priority;

        private LocationDto location;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocationDto {
        private String street;
        private String number;
        private String city;
        private String state;
        private String zipcode;
    }

    // ---- RESPONSE ----

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EvaluationResponse {
        private boolean success;
        private String code;
        private String message;
        private EvaluationData data;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EvaluationData {
        private Integer occurrenceId;

        /** Status final após avaliação da IA */
        private String status;

        /** Se este é o primeiro tratamento desta ocorrência */
        private Boolean isNew;

        /** Mensagem curta da prefeitura gerada pela IA */
        private String prefeituraMessage;

        /** Quantas vezes esta ocorrência já foi avaliada (incluindo esta) */
        private Long totalEvaluations;

        /** Histórico resumido das últimas avaliações */
        private List<EvaluationHistoryItem> history;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationHistoryItem {
        private String previousStatus;
        private String newStatus;
        private String message;
        private LocalDateTime evaluatedAt;
    }

    // ---- Anthropic API (interno) ----

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnthropicRequest {
        private String model;
        private int max_tokens;
        private List<AnthropicMessage> messages;
        private String system;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnthropicMessage {
        private String role;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnthropicResponse {
        private List<ContentBlock> content;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ContentBlock {
            private String type;
            private String text;
        }
    }

    // ---- AI parsed result (interno) ----

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiEvaluationResult {
        private String finalStatus;
        private String message;
    }
}
