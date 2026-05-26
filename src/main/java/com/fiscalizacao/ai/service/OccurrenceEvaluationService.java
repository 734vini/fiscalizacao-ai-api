package com.fiscalizacao.ai.service;

import com.fiscalizacao.ai.dto.OccurrenceDto.*;
import com.fiscalizacao.ai.model.OccurrenceEvaluation;
import com.fiscalizacao.ai.model.OccurrenceStatus;
import com.fiscalizacao.ai.repository.OccurrenceEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orquestra o fluxo completo de avaliação:
 * 1. Consulta histórico da ocorrência
 * 2. Chama a IA com contexto completo
 * 3. Persiste o resultado
 * 4. Retorna resposta estruturada
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OccurrenceEvaluationService {

    private final OccurrenceEvaluationRepository repository;
    private final AnthropicAiService aiService;

    // Quantas avaliações anteriores enviar como contexto para a IA
    private static final int HISTORY_CONTEXT_LIMIT = 5;

    @Transactional
    public EvaluationResponse evaluate(EvaluationRequest request) {
        log.info("Iniciando avaliação - occurrenceId={}, statusAtual={}", request.getOccurrenceId(), request.getStatus());

        // Valida se o status enviado é um dos valores aceitos
        OccurrenceStatus.fromLabel(request.getStatus()); // lança exceção se inválido

        // 1. Verifica se é uma ocorrência nova (sem histórico neste serviço)
        boolean isNew = !repository.existsByOccurrenceId(request.getOccurrenceId());

        // 2. Busca histórico para contexto da IA
        List<OccurrenceEvaluation> recentHistory = repository
                .findRecentByOccurrenceId(request.getOccurrenceId(), HISTORY_CONTEXT_LIMIT);

        // 3. Chama a IA — envia o status atual, ela decide o próximo
        AiEvaluationResult aiResult = aiService.evaluate(request, recentHistory);

        log.info("IA retornou - nextStatus={}, message={}", aiResult.getFinalStatus(), aiResult.getMessage());

        // 4. Persiste o resultado
        OccurrenceEvaluation evaluation = OccurrenceEvaluation.builder()
                .occurrenceId(request.getOccurrenceId())
                .previousStatus(request.getStatus())   // status que veio do front = atual
                .requestedStatus(request.getStatus())
                .finalStatus(aiResult.getFinalStatus()) // próximo status definido pela IA
                .aiMessage(aiResult.getMessage())
                .isNewOccurrence(isNew)
                .occurrenceTitle(request.getTitle())
                .occurrenceCategory(request.getCategory())
                .evaluatedAt(LocalDateTime.now())
                .build();

        repository.save(evaluation);

        // 5. Monta histórico resumido para a resposta
        List<EvaluationHistoryItem> historyItems = buildHistoryItems(recentHistory);
        long totalEvaluations = repository.countByOccurrenceId(request.getOccurrenceId());

        // 6. Retorna resposta
        return EvaluationResponse.builder()
                .success(true)
                .code("EVALUATION_SUCCESS")
                .message("Avaliação concluída com sucesso")
                .data(EvaluationData.builder()
                        .occurrenceId(request.getOccurrenceId())
                        .status(aiResult.getFinalStatus())
                        .isNew(isNew)
                        .prefeituraMessage(aiResult.getMessage())
                        .totalEvaluations(totalEvaluations)
                        .history(historyItems)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Retorna o histórico completo de avaliações de uma ocorrência.
     */
    @Transactional(readOnly = true)
    public EvaluationResponse getHistory(Integer occurrenceId) {
        List<OccurrenceEvaluation> history =
                repository.findByOccurrenceIdOrderByEvaluatedAtDesc(occurrenceId);

        List<EvaluationHistoryItem> items = buildHistoryItems(history);
        long total = repository.countByOccurrenceId(occurrenceId);

        String lastStatus = history.isEmpty() ? null : history.get(0).getFinalStatus();

        return EvaluationResponse.builder()
                .success(true)
                .code("HISTORY_SUCCESS")
                .message("Histórico recuperado com sucesso")
                .data(EvaluationData.builder()
                        .occurrenceId(occurrenceId)
                        .status(lastStatus)
                        .isNew(history.isEmpty())
                        .totalEvaluations(total)
                        .history(items)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private List<EvaluationHistoryItem> buildHistoryItems(List<OccurrenceEvaluation> evaluations) {
        return evaluations.stream()
                .map(e -> EvaluationHistoryItem.builder()
                        .previousStatus(e.getPreviousStatus())
                        .newStatus(e.getFinalStatus())
                        .message(e.getAiMessage())
                        .evaluatedAt(e.getEvaluatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
