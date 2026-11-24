import hashlib,os,httpx
from fastapi import FastAPI,File,HTTPException,UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from .ocr_engine import scan_receipt
app=FastAPI(title="Warranty Asset Vault OCR",version="1.0.0")
app.add_middleware(CORSMiddleware,allow_origins=["*"],allow_methods=["*"],allow_headers=["*"],allow_credentials=False)
JAVA_URL=os.getenv("JAVA_SERVICE_URL","http://localhost:8080").rstrip("/")
class EvaluationRequest(BaseModel):
 purchase_date:str
 purchase_price:float=0
 components:list[dict]
@app.get("/health")
def health():return {"status":"ok","service":"python-ocr"}
@app.post("/api/v1/receipts/scan")
async def scan(file:UploadFile=File(...)):
 content=await file.read()
 if not content:raise HTTPException(400,"Uploaded file is empty")
 try:fields=scan_receipt(content)
 except ValueError as exc:raise HTTPException(400,str(exc)) from exc
 return {"sha256_hash":hashlib.sha256(content).hexdigest(),"fields":fields}
@app.post("/api/v1/assets/evaluate")
async def evaluate(payload:EvaluationRequest):
 try:
  async with httpx.AsyncClient(timeout=15) as client:
   response=await client.post(f"{JAVA_URL}/api/evaluate",json=payload.model_dump());response.raise_for_status();return response.json()
 except httpx.HTTPError as exc:raise HTTPException(502,f"Core Java service unavailable: {exc}") from exc
