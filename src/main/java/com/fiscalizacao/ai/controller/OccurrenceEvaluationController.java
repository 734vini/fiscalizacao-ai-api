package com.fiscalizacao.ai.controller;

import com.fiscalizacao.ai.dto.OccurrenceDto.*;
import com.fiscalizacao.ai.service.OccurrenceEvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controller principal da API de avaliação de ocorrências por IA.
 *
 * Recebe o JSON completo da ocorrência vindo do app-fiscalizacao-urbana,
 * consulta o histórico local, chama a IA e retorna o próximo status
 * junto com a mensagem institucional da prefeitura.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class OccurrenceEvaluationController {

    private final OccurrenceEvaluationService evaluationService;

    /**
     * Avalia uma ocorrência e retorna o próximo status definido pela IA.
     *
     * POST /api/ai/evaluate
     *
     * Body (campos obrigatórios: occurrenceId, title, category, status):
     * {
     *   "occurrenceId": 12,
     *   "title": "Buraco na Rua Principal",
     *   "description": "Buraco grande e perigoso",
     *   "category": "Buraco na Pista",
     *   "status": "ABERTO",
     *   "priority": 3,
     *   "location": {
     *     "street": "Rua Teste",
     *     "number": "123",
     *     "city": "Itajaí",
     *     "state": "SC",
     *     "zipcode": "88301-000"
     *   }
     * }
     *
     * Campos ignorados (enviados pelo front mas não usados pela IA):
     *   reporter, imageUrl, recurrenceCount, commentCount, createdAt, updatedAt
     */
    @PostMapping("/evaluate")
    public ResponseEntity<EvaluationResponse> evaluate(
            @Valid @RequestBody EvaluationRequest request
    ) {
        log.info("POST /api/ai/evaluate - occurrenceId={}, status={}, category={}",
                request.getOccurrenceId(), request.getStatus(), request.getCategory());

        return ResponseEntity.ok(evaluationService.evaluate(request));
    }

    /**
     * Retorna o histórico completo de avaliações de uma ocorrência.
     * Útil para exibir um "log de atualizações da prefeitura" no front-end.
     *
     * GET /api/ai/history?occurrenceId=42
     */
    @GetMapping("/history")
    public ResponseEntity<EvaluationResponse> getHistory(
            @RequestParam @NotNull Integer occurrenceId
    ) {
        log.info("GET /api/ai/history - occurrenceId={}", occurrenceId);
        return ResponseEntity.ok(evaluationService.getHistory(occurrenceId));
    }

    /**
     * Health check simples.
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(new HealthResponse("UP", LocalDateTime.now()));
    }

    record HealthResponse(String status, LocalDateTime timestamp) {}
}
