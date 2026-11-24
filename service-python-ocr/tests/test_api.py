from fastapi.testclient import TestClient
from app.main import app
from app.ocr_engine import extract_fields
client=TestClient(app)
def test_health():
 r=client.get('/health');assert r.status_code==200 and r.json()['status']=='ok'
def test_regex_extraction():
 f=extract_fields('GSTIN: 27AAPFU0939F1ZV Serial No: ABC123456789 Date: 14/02/2025 Total: Rs. 12,499.00')
 assert f['gstin']=='27AAPFU0939F1ZV';assert f['serial_number']=='ABC123456789';assert f['purchase_price']==12499.0
