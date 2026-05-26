package com.fiscalizacao.ai.model;

import java.util.Arrays;

/**
 * Status válidos para ocorrências do sistema de fiscalização urbana.
 * Reflete exatamente os valores usados no app-fiscalizacao-urbana (Next.js).
 */
public enum OccurrenceStatus {

    ABERTO("Aberto"),
    EM_ANALISE("Em analise"),
    EM_EXECUCAO("Em execucao"),
    FINALIZADO("Finalizado");

    private final String label;

    OccurrenceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Retorna o próximo status natural na progressão de uma ocorrência.
     * A IA usa isso como sugestão base, mas pode decidir diferente dependendo do contexto.
     */
    public OccurrenceStatus next() {
        return switch (this) {
            case ABERTO -> EM_ANALISE;
            case EM_ANALISE -> EM_EXECUCAO;
            case EM_EXECUCAO -> FINALIZADO;
            case FINALIZADO -> FINALIZADO; // já encerrado
        };
    }

    /**
     * Converte a label (string do front-end) para o enum.
     * Ex: "Em analise" → EM_ANALISE
     */
    public static OccurrenceStatus fromLabel(String label) {
        return Arrays.stream(values())
                .filter(s -> s.label.equalsIgnoreCase(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Status inválido: '" + label + "'. Valores aceitos: " +
                        Arrays.stream(values()).map(OccurrenceStatus::getLabel)
                              .reduce((a, b) -> a + ", " + b).orElse("")
                ));
    }

    /**
     * Retorna todas as labels válidas como string formatada (para prompts e mensagens de erro).
     */
    public static String allLabels() {
        return Arrays.stream(values())
                .map(OccurrenceStatus::getLabel)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
