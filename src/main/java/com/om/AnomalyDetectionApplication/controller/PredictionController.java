package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.service.PredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class PredictionController {

    private final PredictionService predictionService;

    @Autowired
    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/api/anomalies/predict")
    public MlPredictionResponse predict() {
        return predictionService.predict();
    }
    
    @PostMapping("/api/anomalies/train")
    public Map train() {
        return predictionService.trainModel();
    }
    
    @GetMapping("/anomalies-only")
    public List<MlPredictionResultDTO> getAnomaliesOnly() {
        return predictionService.predict()
                .getResults()
                .stream()
                .filter(p -> p.getPrediction() == -1)
                .toList();
    }
    
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<MlPredictionResultDTO> data = predictionService.predict().getResults();

        long anomalies = data.stream()
                .filter(p -> p.getPrediction() == -1)
                .count();

        return Map.of(
                "total", data.size(),
                "anomalies", anomalies,
                "percentage", Math.round((anomalies * 10000.0) / data.size()) / 100.0,
                "ratio", anomalies + "/" + data.size()
        );
    }
}