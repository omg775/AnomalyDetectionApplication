# 🚀 Anomaly Detection Application

A high-performance **Spring Boot 4** backend system designed to detect anomalies in network traffic using **Java 25** and **Supabase**.

## 📌 Features
* **Bulk CSV Ingestion:** Upload via `POST /api/traffic/upload`.
* **Heuristic Detection:** Automatically flags high-volume traffic.
* **Cloud Persistence:** Powered by Supabase (PostgreSQL).
* **Auto-Schema:** Hibernate handles table generation on startup.

## 🛠️ Tech Stack
* **Java 25** | **Spring Boot 4** | **PostgreSQL** | **Apache Commons CSV**

## ⚙️ Quick Start
1. **Clone:** `git clone https://github.com/your-username/anomaly-detection.git`
2. **Configure:** Set `spring.datasource.url` in `application.properties`.
3. **Run:** `mvn spring-boot:run`

## 📡 API
* `POST /api/traffic/upload` - Upload CSV file.
* `POST /api/traffic/detect` - Run manual detection logic.
* `GET /api/traffic/alerts` - Retrieve all detected anomalies.

---
*Created for network security monitoring.*
