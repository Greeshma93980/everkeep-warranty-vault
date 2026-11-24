#### Everkeep — Warranty Asset Vault

A multi-service household asset and multi-component warranty tracker built with **React**, **Python (FastAPI + OpenCV + Tesseract)**, **Core Java 17 (HttpServer + Apache PDFBox)**, **PostgreSQL**, and **Docker**. 

#### Why This Project Exists

High-value household appliances (ACs, front-load washing machines, refrigerators, laptops, and inverter batteries) often come with **split, multi-component warranties** (e.g., *2 years comprehensive on the main unit + 10 years on the direct-drive motor*) and faded offline thermal receipts. **Everkeep** turns unstructured receipt photos into searchable, component-level warranty timelines and generates tamper-proof **SHA-256 verified Claim & Resale Dossier PDFs** on demand. 

#### Key Features

* **Receipt Preprocessing & OCR (Python):** Enhances faded thermal receipts using **OpenCV** (grayscale, denoising, adaptive thresholding) and extracts GSTINs, Serial/IMEI numbers, purchase dates, amounts, and split warranty durations via **Tesseract OCR**.
* **Human-in-the-Loop Verification (React):** Opens an editable *Review & Confirm* modal pre-filled with OCR results before adding assets to the household workspace.
* **Multi-Component & Prorated Warranty Engine (Core Java 17):** Evaluates independent component expiry timelines (STANDARD vs. PRORATED), runs a background ScheduledExecutorService check, and classifies upcoming expiries into escalation tiers.
* **Tamper-Proof PDF Dossiers (Apache PDFBox):** Embeds a cryptographic **SHA-256 hash** of the original uploaded receipt into a downloadable **Warranty Claim & Resale Dossier PDF** for service technicians or second-hand buyers.
* **Technician / Resale Mode:** Dedicated toggle view revealing purchase valuation and one-click PDF dossier export.

#### Architecture

Show Code

Hide Code

Download diagram SVG

ASCII

[React Dashboard :5173] -- /api/v1 --> [FastAPI OCR :8000] -- /api/evaluate --> [Core Java :8080]

         |                                                                             |
         +----------------------------- POST /api/dossier ---------------------------->+
                                                                                       |
[PostgreSQL 15 :5432] <-- schema ready (database/schema.sql); persistence integration planned

Use code with caution.

Copy code text to clipboard.

#### Run Locally

**Requirements:** Java 17+, Maven 3.9+, Python 3.11+ with Tesseract OCR, Node.js 20+, PostgreSQL 15 (optional). 

1. **Java Engine (:8080):** 

bash

cd engine-java-core
mvn test package
java -jar target/engine-java-core-1.0.0-jar-with-dependencies.jar

Use code with caution.

Copy code text to clipboard.
2. **Python OCR Gateway (:8000):** 

bash

cd service-python-ocr
python -m venv venv
.\venv\Scripts\Activate.ps1  # Windows (or: source venv/bin/activate on macOS/Linux)
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000

Use code with caution.

Copy code text to clipboard.
3. **React Frontend (:5173):** 

bash

cd frontend-react
npm install
npm run dev

Use code with caution.

Copy code text to clipboard.

Open http://localhost:5173.
4. **Database Schema (Optional):**
Create database warranty_vault and apply the schema: 

bash

psql -U vault -d warranty_vault -f database/schema.sql

Use code with caution.

Copy code text to clipboard.

#### Docker Compose

Run the entire stack with a single command from the project root: 

bash

docker compose up --build

Use code with caution.

Copy code text to clipboard.

* **Dashboard:** http://localhost:5173
* **Python OCR API Docs:** http://localhost:8000/docs
* **Core Java Engine:** http://localhost:8080
* **PostgreSQL:** localhost:5432

Stop containers with docker compose down. 

#### API Endpoints

* GET /health — Health check on both Python (:8000) and Java (:8080) services.
* POST http://localhost:8000/api/v1/receipts/scan — Multipart file upload; returns OCR-extracted fields, component warranties, and SHA-256 hash.
* POST http://localhost:8000/api/v1/assets/evaluate (or Java POST /api/evaluate) — Evaluates purchase_date, purchase_price, and components (name, type, duration_months, optional replacement_value).
* POST http://localhost:8080/api/dossier — Accepts asset metadata, component coverage list, and sha256_hash; returns a downloadable PDF dossier.

**Note:** OCR extraction is best-effort and reviewed via the confirmation modal before saving. Current frontend asset state runs in-memory for zero-dependency local demos, with the normalized PostgreSQL schema (database/schema.sql) prepared for full persistence integration.