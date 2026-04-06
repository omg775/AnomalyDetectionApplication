import { useEffect, useState } from "react";

const API_BASE = "http://localhost:8080/api/anomalies";

function App() {
  const [summary, setSummary] = useState(null);
  const [anomalies, setAnomalies] = useState([]);

  useEffect(() => {
    fetch(`${API_BASE}/summary`)
      .then((res) => res.json())
      .then((data) => setSummary(data));

    fetch(`${API_BASE}/anomalies-only`)
      .then((res) => res.json())
      .then((data) => setAnomalies(data));
  }, []);

  return (
    <div style={{ padding: "20px", fontFamily: "Arial" }}>
      <h1>🚀 Anomaly Detection Dashboard</h1>

      <h2>📊 Summary</h2>
      {summary ? (
        <div>
          <p>Total: {summary.totalRecords}</p>
          <p>Anomalies: {summary.anomalyCount}</p>
          <p>Normal: {summary.normalCount}</p>
          <p>Percentage: {summary.anomalyPercentage}%</p>
        </div>
      ) : (
        <p>Loading...</p>
      )}

      <h2>🔴 Anomalies</h2>
      <table border="1" cellPadding="8">
        <thead>
          <tr>
            <th>Prediction</th>
            <th>Score</th>
            <th>Severity</th>
            <th>Hour</th>
            <th>Day</th>
          </tr>
        </thead>
        <tbody>
          {anomalies.slice(0, 20).map((item, i) => (
            <tr key={i}>
              <td>{item.prediction}</td>
              <td>{item.score}</td>
              <td>{item.severity}</td>
              <td>{item.hourOfDay}</td>
              <td>{item.dayOfWeek}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default App;