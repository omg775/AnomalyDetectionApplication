🚀 Anomaly Detection Application

A Spring Boot backend system that detects anomalies in network traffic using CSV input. The application processes uploaded data, analyzes patterns, and stores results in a Supabase (PostgreSQL) database.

📌 Features
Upload network traffic data via CSV
Detect anomalies based on traffic patterns
Store results using JPA + Hibernate
Supabase (PostgreSQL) integration
REST API for full workflow
Automatic table creation
🛠️ Tech Stack
Java 25
Spring Boot 4
PostgreSQL (Supabase)
Spring Data JPA + Hibernate
Apache Commons CSV
Maven
⚙️ Setup
1. Clone the repo
git clone https://github.com/your-username/anomaly-detection.git
cd anomaly-detection
2. Configure database

Update application.properties:

spring.datasource.url=jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
spring.datasource.username=postgres.<your-project-ref>
spring.datasource.password=YOUR_PASSWORD

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
3. Run the app
mvn spring-boot:run

App runs at:

http://localhost:8080
📡 API
Upload CSV
POST /api/traffic/upload

Body → form-data

key: file
type: File
Run Detection
POST /api/traffic/detect
Get Alerts
GET /api/traffic/alerts
📄 CSV Format
timestamp,source_ip,destination_ip,destination_port,protocol,bytes_transferred
2026-01-01 10:00:00,192.168.1.1,10.0.0.1,80,TCP,500
🧠 Workflow
Upload CSV
Data stored in traffic_records
Detection runs
Results saved in anomaly_alerts
⚠️ Notes
Uses Supabase Session Pooler (IPv4 compatible)
SSL required (sslmode=require)
Tables auto-created by Hibernate
📈 Future Improvements
Real-time detection
ML-based anomaly detection
Frontend dashboard
Alerts (email/webhooks)
