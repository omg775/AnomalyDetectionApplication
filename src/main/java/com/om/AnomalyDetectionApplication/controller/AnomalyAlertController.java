//package com.om.AnomalyDetectionApplication.controller;
//
//import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
//import com.om.AnomalyDetectionApplication.service.AnomalyAlertService;
//import org.springframework.data.domain.Page;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/traffic")
//@CrossOrigin
//public class AnomalyAlertController {
//
//    private final AnomalyAlertService anomalyAlertService;
//
//    public AnomalyAlertController(AnomalyAlertService anomalyAlertService) {
//        this.anomalyAlertService = anomalyAlertService;
//    }
//
//    @GetMapping("/alerts")
//    public Page<AnomalyAlert> getAlerts(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @RequestParam(required = false) String severity,
//            @RequestParam(required = false) String anomalyType
//    ) {
//        return anomalyAlertService.getAlerts(page, size, severity, anomalyType);
//    }
//}