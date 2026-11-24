import re
import shutil
from pathlib import Path

import cv2
import numpy as np
import pytesseract


_tesseract_path = shutil.which("tesseract")
if not _tesseract_path:
    for _candidate in (
        Path(r"C:\Program Files\Tesseract-OCR\tesseract.exe"),
        Path(r"C:\Users\ambav\AppData\Local\Programs\Tesseract-OCR\tesseract.exe"),
    ):
        if _candidate.is_file():
            _tesseract_path = str(_candidate)
            break
if _tesseract_path:
    pytesseract.pytesseract.tesseract_cmd = _tesseract_path


GSTIN_RE = re.compile(r"\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]\b", re.I)
SERIAL_RE = re.compile(
    r"\b(?:serial(?:\s*(?:/\s*imei|no\.?|number))?|s\s*/\s*n|imei)\s*[:#-]?\s*"
    r"([A-Z0-9][A-Z0-9/-]{5,24})",
    re.I,
)
DATE_RE = re.compile(
    r"\b(\d{4}-\d{1,2}-\d{1,2}|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|"
    r"\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})\b"
)
AMOUNT_RE = re.compile(
    r"(?:grand\s*total|total\s*amount|amount\s*paid|net\s*amount|invoice\s*total|total)"
    r"\s*[:#-]?\s*(?:(?:INR|Rs\.?|\u20b9)\s*)?([0-9][0-9,]*(?:\.\d{1,2})?)",
    re.I,
)
TITLE_LABEL_RE = re.compile(
    r"^\s*(?:product(?:\s+(?:name|title|description))?|item\s+name|model)\s*[:#-]\s*(.+)$",
    re.I,
)
WARRANTY_LABEL_RE = re.compile(r"\b(?:warrant(?:y|ies)|coverage)\b\s*[:#-]?\s*(.*)", re.I)
WARRANTY_TERM_RE = re.compile(
    r"(?P<years>\d+(?:\.\d+)?)\s*(?:years?|yrs?|y)\b|"
    r"(?P<months>\d+(?:\.\d+)?)\s*(?:months?|mos?|mo)\b",
    re.I,
)
WARRANTY_QUALIFIER_RE = re.compile(
    r"\b(?:comprehensive|warranty|warranties|coverage|for|of)\b", re.I
)
NON_TITLE_RE = re.compile(
    r"\b(?:tax\s+invoice|invoice|receipt|gstin|serial|imei|date|total|amount|"
    r"qty|quantity|price|address|phone|email|customer|bill\s+to|sold\s+by)\b",
    re.I,
)


def _normalize_date(value):
    """Return ISO date text when a common receipt date format is recognized."""
    value = value.strip()
    for pattern in (
        "%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y", "%d/%m/%y", "%d-%m-%y",
        "%d %B %Y", "%d %b %Y",
    ):
        try:
            from datetime import datetime

            return datetime.strptime(value, pattern).date().isoformat()
        except ValueError:
            continue
    return value


def _extract_product_title(lines):
    for line in lines:
        match = TITLE_LABEL_RE.match(line)
        if match:
            title = match.group(1).strip(" :-|\t")
            if title:
                return title

    for line in lines:
        candidate = line.strip(" :-|\t")
        if not candidate or len(candidate) < 4 or len(candidate) > 100:
            continue
        if NON_TITLE_RE.search(candidate) or GSTIN_RE.search(candidate) or SERIAL_RE.search(candidate):
            continue
        if re.search(r"\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b", candidate):
            continue
        if re.search(r"(?:₹|\b(?:INR|Rs\.?))\s*\d", candidate, re.I):
            continue
        if re.fullmatch(r"[\W\d_]+", candidate):
            continue
        return candidate
    return None


def _parse_warranty_components(text):
    """Parse duration and component pairs from warranty lines or inline terms."""
    components = []
    seen = set()
    for line in text.splitlines():
        if not WARRANTY_LABEL_RE.search(line) and not WARRANTY_TERM_RE.search(line):
            continue
        segments = re.split(r"\s*[|;]\s*", line)
        for segment in segments:
            term = WARRANTY_TERM_RE.search(segment)
            if not term:
                continue
            if term.group("years"):
                months = round(float(term.group("years")) * 12)
            else:
                months = round(float(term.group("months")))

            component_name = segment[term.end():].strip(" :-|,.")
            component_name = WARRANTY_QUALIFIER_RE.sub(" ", component_name)
            component_name = re.sub(r"\s+", " ", component_name).strip(" :-|,.")
            if not component_name:
                before_duration = segment[:term.start()]
                component_name = WARRANTY_QUALIFIER_RE.sub(" ", before_duration)
                component_name = re.sub(r"\s+", " ", component_name).strip(" :-|,.")
            if not component_name:
                component_name = "Main Unit"

            key = (component_name.casefold(), months)
            if months > 0 and key not in seen:
                seen.add(key)
                components.append({
                    "name": component_name,
                    "duration_months": months,
                    "warranty_type": "PRORATED" if re.search(r"\bprorat(?:ed|a)\b", segment, re.I) else "STANDARD",
                })
    return components


def extract_fields(text):
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    gstin = GSTIN_RE.search(text)
    serial = SERIAL_RE.search(text)
    date = DATE_RE.search(text)
    amount = AMOUNT_RE.search(text)

    return {
        "product_title": _extract_product_title(lines),
        "serial_number": serial.group(1).upper() if serial else None,
        "gstin": gstin.group(0).upper() if gstin else None,
        "purchase_date": _normalize_date(date.group(1)) if date else None,
        "purchase_price": float(amount.group(1).replace(",", "")) if amount else None,
        "warranty_components": _parse_warranty_components(text),
        "raw_text": text,
    }


def scan_receipt(image_bytes):
    image = cv2.imdecode(np.frombuffer(image_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("Uploaded file is not a readable image")

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    denoised = cv2.fastNlMeansDenoising(gray, None, 10, 7, 21)
    enhanced = cv2.adaptiveThreshold(
        denoised, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 31, 11
    )
    text = pytesseract.image_to_string(enhanced, config="--psm 6")
    return extract_fields(text)
