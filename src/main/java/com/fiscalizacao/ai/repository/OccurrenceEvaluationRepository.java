package com.fiscalizacao.ai.repository;

import com.fiscalizacao.ai.model.OccurrenceEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OccurrenceEvaluationRepository extends JpaRepository<OccurrenceEvaluation, Long> {

    /**
     * Retorna o histórico completo de avaliações de uma ocorrência, do mais recente ao mais antigo.
     */
    List<OccurrenceEvaluation> findByOccurrenceIdOrderByEvaluatedAtDesc(Integer occurrenceId);

    /**
     * Retorna a avaliação mais recente de uma ocorrência.
     */
    Optional<OccurrenceEvaluation> findTopByOccurrenceIdOrderByEvaluatedAtDesc(Integer occurrenceId);

    /**
     * Conta quantas avaliações uma ocorrência já teve.
     */
    long countByOccurrenceId(Integer occurrenceId);

    /**
     * Verifica se a ocorrência já foi avaliada alguma vez.
     */
    boolean existsByOccurrenceId(Integer occurrenceId);

    /**
     * Retorna os últimos N registros de uma ocorrência para contexto da IA.
     */
    @Query("SELECT e FROM OccurrenceEvaluation e WHERE e.occurrenceId = :occurrenceId ORDER BY e.evaluatedAt DESC LIMIT :limit")
    List<OccurrenceEvaluation> findRecentByOccurrenceId(Integer occurrenceId, int limit);
}
