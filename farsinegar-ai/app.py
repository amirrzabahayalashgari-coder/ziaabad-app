import os, uuid, threading
from pathlib import Path
from flask import Flask, request, jsonify, render_template, send_file
from werkzeug.utils import secure_filename
from dotenv import load_dotenv
from docx import Document
from faster_whisper import WhisperModel
load_dotenv()
BASE=Path(__file__).resolve().parent
DATA=BASE/"data"; DATA.mkdir(exist_ok=True)
app=Flask(__name__)
app.config["MAX_CONTENT_LENGTH"]=int(os.getenv("MAX_UPLOAD_BYTES",str(2*1024*1024*1024)))
jobs={}; jobs_lock=threading.Lock(); inference_lock=threading.Lock(); model_instance=None
ALLOWED={".mp3",".wav",".m4a",".mp4",".mpeg",".mpga",".webm",".ogg",".flac"}
def update(job_id,**values):
    with jobs_lock:
        if job_id in jobs: jobs[job_id].update(values)
def get_model():
    global model_instance
    if model_instance is None:
        name=os.getenv("WHISPER_MODEL","small")
        device=os.getenv("WHISPER_DEVICE","cpu")
        compute=os.getenv("WHISPER_COMPUTE_TYPE","int8" if device=="cpu" else "float16")
        model_instance=WhisperModel(name,device=device,compute_type=compute)
    return model_instance
def work(job_id,source):
    try:
        update(job_id,state="processing",message="بارگذاری مدل محلی Whisper؛ اجرای اول ممکن است طول بکشد…",progress=3)
        with inference_lock:
            model=get_model()
            update(job_id,message="مدل آماده است؛ در حال تبدیل گفتار فارسی…",progress=8)
            segments,info=model.transcribe(source,language="fa",task="transcribe",beam_size=5,vad_filter=True,condition_on_previous_text=True)
            duration=float(getattr(info,"duration",0) or 0); parts=[]
            for seg in segments:
                if seg.text.strip(): parts.append(seg.text.strip())
                pct=min(96,8+int((float(seg.end)/duration)*88)) if duration else 8
                update(job_id,transcript="\n".join(parts),progress=pct,message="در حال پیاده‌سازی گفتار فارسی…")
        final="\n".join(parts).strip()
        if not final: raise RuntimeError("متنی از فایل دریافت نشد؛ کیفیت صدا یا زبان فایل را بررسی کنید.")
        update(job_id,state="done",message="تبدیل کامل شد؛ پردازش به‌صورت محلی انجام شد.",progress=100,transcript=final)
    except Exception as exc:
        update(job_id,state="error",message=f"خطا: {exc}",progress=0)
    finally:
        Path(source).unlink(missing_ok=True)
@app.get("/")
def index(): return render_template("index.html")
@app.get("/api/health")
def health(): return jsonify(engine="faster-whisper",api_required=False,model=os.getenv("WHISPER_MODEL","small"))
@app.post("/api/transcribe")
def transcribe():
    audio=request.files.get("audio")
    if not audio or not audio.filename: return jsonify(error="یک فایل صوتی انتخاب کنید."),400
    ext=Path(secure_filename(audio.filename)).suffix.lower()
    if ext not in ALLOWED: return jsonify(error="فرمت فایل پشتیبانی نمی‌شود."),400
    job_id=uuid.uuid4().hex; folder=DATA/job_id; folder.mkdir(exist_ok=True)
    source=folder/("audio"+ext); audio.save(source)
    with jobs_lock: jobs[job_id]={"state":"queued","progress":1,"message":"فایل دریافت شد؛ در صف پردازش محلی…","transcript":""}
    threading.Thread(target=work,args=(job_id,str(source)),daemon=True).start()
    return jsonify(job_id=job_id)
@app.get("/api/status/<job_id>")
def status(job_id):
    with jobs_lock:
        data=jobs.get(job_id)
        return (jsonify(data),200) if data else (jsonify(error="درخواست پیدا نشد."),404)
@app.post("/api/export/docx")
def export_docx():
    text=(request.json or {}).get("text","").strip()
    if not text: return jsonify(error="متنی برای خروجی وجود ندارد."),400
    doc=Document(); p=doc.add_paragraph(); p.paragraph_format.alignment=2; p.add_run(text)
    out=DATA/("transcript-"+uuid.uuid4().hex+".docx"); doc.save(out)
    return send_file(out,as_attachment=True,download_name="متن-پیاده-سازی-شده.docx")
@app.errorhandler(413)
def too_large(_): return jsonify(error="حجم فایل از محدودیت سرور بیشتر است."),413
if __name__=="__main__": app.run(host="0.0.0.0",port=int(os.getenv("PORT","5000")),debug=False,threaded=True)
