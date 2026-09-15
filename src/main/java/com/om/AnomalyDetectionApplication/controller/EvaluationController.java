package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.dto.AgreementResponse;
import com.om.AnomalyDetectionApplication.service.EvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/agreement")
    public ResponseEntity<AgreementResponse> getAgreement() {
        return ResponseEntity.ok(evaluationService.computeAgreement());
    }
}
