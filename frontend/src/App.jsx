import React, { useEffect, useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AlertTriangle, Activity, Database, RefreshCw } from "lucide-react";

const API_BASE = "http://localhost:8080/api/anomalies";

export default function AnomalyDashboard() {
  const [summary, setSummary] = useState(null);
  const [anomalies, setAnomalies] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const fetchDashboardData = async () => {
    setLoading(true);
    setError("");

    try {
      const [summaryRes, anomaliesRes] = await Promise.all([
        fetch(`${API_BASE}/summary`),
        fetch(`${API_BASE}/anomalies-only`),
      ]);

      if (!summaryRes.ok) {
        throw new Error("Failed to load summary data");
      }

      if (!anomaliesRes.ok) {
        throw new Error("Failed to load anomaly data");
      }

      const summaryData = await summaryRes.json();
      const anomaliesData = await anomaliesRes.json();

      setSummary(summaryData);
      setAnomalies(Array.isArray(anomaliesData) ? anomaliesData : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const topFiveAnomalies = useMemo(() => anomalies.slice(0, 5), [anomalies]);

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-7xl space-y-6">
        <div className="flex flex-col gap-4 rounded-3xl bg-white p-6 shadow-sm md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-900">
              Network Anomaly Dashboard
            </h1>
            <p className="mt-2 text-sm text-slate-600">
              Spring Boot + Supabase + ML monitoring frontend
            </p>
          </div>

          <Button onClick={fetchDashboardData} disabled={loading} className="rounded-2xl">
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            Refresh Data
          </Button>
        </div>

        {error && (
          <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="grid gap-4 md:grid-cols-3">
          <Card className="rounded-3xl shadow-sm">
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
                <Database className="h-4 w-4" />
                Total Records
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-slate-900">
                {summary?.totalRecords ?? "-"}
              </div>
            </CardContent>
          </Card>

          <Card className="rounded-3xl shadow-sm">
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
                <AlertTriangle className="h-4 w-4" />
                Anomalies
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-slate-900">
                {summary?.anomalyCount ?? "-"}
              </div>
            </CardContent>
          </Card>

          <Card className="rounded-3xl shadow-sm">
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
                <Activity className="h-4 w-4" />
                Anomaly Percentage
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-slate-900">
                {summary?.anomalyPercentage != null ? `${summary.anomalyPercentage}%` : "-"}
              </div>
            </CardContent>
          </Card>
        </div>

        <Card className="rounded-3xl shadow-sm">
          <CardHeader>
            <CardTitle className="text-slate-900">Top Detected Anomalies</CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <p className="text-sm text-slate-500">Loading anomaly results...</p>
            ) : topFiveAnomalies.length === 0 ? (
              <p className="text-sm text-slate-500">No anomalies found.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-left text-sm">
                  <thead>
                    <tr className="border-b text-slate-500">
                      <th className="px-3 py-3 font-medium">Prediction</th>
                      <th className="px-3 py-3 font-medium">Score</th>
                      <th className="px-3 py-3 font-medium">Severity</th>
                      <th className="px-3 py-3 font-medium">Hour</th>
                      <th className="px-3 py-3 font-medium">Day</th>
                    </tr>
                  </thead>
                  <tbody>
                    {topFiveAnomalies.map((item, index) => (
                      <tr key={index} className="border-b last:border-0">
                        <td className="px-3 py-3 font-medium text-red-600">
                          {item.prediction}
                        </td>
                        <td className="px-3 py-3 text-slate-700">
                          {typeof item.score === "number" ? item.score.toFixed(4) : item.score ?? "-"}
                        </td>
                        <td className="px-3 py-3 text-slate-700">{item.severity ?? "-"}</td>
                        <td className="px-3 py-3 text-slate-700">{item.hourOfDay ?? "-"}</td>
                        <td className="px-3 py-3 text-slate-700">{item.dayOfWeek ?? "-"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
