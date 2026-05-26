package com.fiscalizacao.ai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Armazena o histórico de avaliações da IA para cada ocorrência.
 * Permite que respostas futuras sejam mais coesas e contextuais.
 */
@Entity
@Table(name = "occurrence_evaluations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccurrenceEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID da ocorrência no sistema Next.js (app-fiscalizacao-urbana)
     */
    @Column(name = "occurrence_id", nullable = false)
    private Integer occurrenceId;

    /**
     * Status anterior à avaliação
     */
    @Column(name = "previous_status")
    private String previousStatus;

    /**
     * Novo status solicitado
     */
    @Column(name = "requested_status", nullable = false)
    private String requestedStatus;

    /**
     * Status final definido pela IA (pode diferir do solicitado se a IA rejeitar)
     */
    @Column(name = "final_status", nullable = false)
    private String finalStatus;

    /**
     * Mensagem curta gerada pela IA (voz da prefeitura)
     */
    @Column(name = "ai_message", nullable = false, length = 1000)
    private String aiMessage;

    /**
     * Se este é o primeiro registro para esta ocorrência
     */
    @Column(name = "is_new_occurrence", nullable = false)
    private Boolean isNewOccurrence;

    /**
     * Título da ocorrência no momento da avaliação
     */
    @Column(name = "occurrence_title")
    private String occurrenceTitle;

    /**
     * Categoria da ocorrência
     */
    @Column(name = "occurrence_category")
    private String occurrenceCategory;

    /**
     * Data e hora da avaliação
     */
    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist
    public void prePersist() {
        this.evaluatedAt = LocalDateTime.now();
    }
}
