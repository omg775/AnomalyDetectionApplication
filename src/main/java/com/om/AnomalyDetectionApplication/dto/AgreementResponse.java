package com.om.AnomalyDetectionApplication.dto;

/**
 * Compares the heuristic layer's HIGH-severity verdict against the ML
 * layer's anomaly flag, alert by alert. "Agree" means both layers reached
 * the same positive/negative verdict (both flagged it, or neither did).
 */
public class AgreementResponse {

    private final int totalCompared;
    private final int bothFlagged;
    private final int heuristicOnly;
    private final int mlOnly;
    private final int neitherFlagged;
    private final double agreementRate;

    public AgreementResponse(int totalCompared, int bothFlagged, int heuristicOnly, int mlOnly,
                              int neitherFlagged, double agreementRate) {
        this.totalCompared = totalCompared;
        this.bothFlagged = bothFlagged;
        this.heuristicOnly = heuristicOnly;
        this.mlOnly = mlOnly;
        this.neitherFlagged = neitherFlagged;
        this.agreementRate = agreementRate;
    }

    public int getTotalCompared() {
        return totalCompared;
    }

    public int getBothFlagged() {
        return bothFlagged;
    }

    public int getHeuristicOnly() {
        return heuristicOnly;
    }

    public int getMlOnly() {
        return mlOnly;
    }

    public int getNeitherFlagged() {
        return neitherFlagged;
    }

    public double getAgreementRate() {
        return agreementRate;
    }
}
