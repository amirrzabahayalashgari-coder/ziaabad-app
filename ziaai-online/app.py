import os, re, uuid, time, sqlite3, secrets, hmac, asyncio, subprocess, tempfile
from pathlib import Path
from datetime import datetime, timezone
import httpx
from fastapi import FastAPI, UploadFile, File, Header, HTTPException, Request, BackgroundTasks
from fastapi.responses import HTMLResponse, PlainTextResponse
from fastapi.staticfiles import StaticFiles

APP_DIR = Path(__file__).parent
DATA = Path(os.getenv("DATA_DIR", str(APP_DIR / "data")))
DATA.mkdir(parents=True, exist_ok=True)
DB = DATA / "ziaai.sqlite3"
MAX_MB = int(os.getenv("MAX_UPLOAD_MB", "500"))
GROQ_KEY = os.getenv("GROQ_API_KEY", "")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "")
MODEL = "whisper-large-v3"
app = FastAPI(title="ZiaAI Online", docs_url=None, redoc_url=None)
admin_tokens = set()
jobs = {}
MAX_ACTIVE_PER_USER = 2

def db():
    c = sqlite3.connect(DB, timeout=30)
    c.row_factory = sqlite3.Row
    return c

def init_db():
    with db() as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS users(
          user_id TEXT PRIMARY KEY, ip TEXT, first_seen TEXT, last_seen TEXT,
          blocked INTEGER DEFAULT 0, request_count INTEGER DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS events(
          id INTEGER PRIMARY KEY AUTOINCREMENT, at TEXT, actor TEXT, action TEXT, target TEXT, detail TEXT
        );
        """)
init_db()

def now():
    return datetime.now(timezone.utc).isoformat()

def event(actor, action, target="", detail=""):
    with db() as c:
        c.execute("INSERT INTO events(at,actor,action,target,detail) VALUES(?,?,?,?,?)",
                  (now(), actor, action, target, detail[:300]))

def user_check(user_id: str, ip: str):
    if not re.fullmatch(r"[A-Za-z0-9_-]{12,80}", user_id or ""):
        raise HTTPException(400, "شناسه کاربر معتبر نیست")
    with db() as c:
        row = c.execute("SELECT * FROM users WHERE user_id=?", (user_id,)).fetchone()
        if row and row["blocked"]:
            raise HTTPException(403, "دسترسی این شناسه محدود شده است")
        if not row:
            c.execute("INSERT INTO users(user_id,ip,first_seen,last_seen) VALUES(?,?,?,?)",
                      (user_id, ip, now(), now()))
        else:
            c.execute("UPDATE users SET ip=?,last_seen=?,request_count=request_count+1 WHERE user_id=?",
                      (ip, now(), user_id))
    return user_id

def require_admin(token):
    if not token or token not in admin_tokens:
        raise HTTPException(401, "ابتدا وارد پنل مدیریت شوید")

@app.get("/api/health")
def health():
    return {"ok": True, "service": "ZiaAI Online", "engine_configured": bool(GROQ_KEY)}

@app.get("/", response_class=HTMLResponse)
def home():
    return (APP_DIR / "index.html").read_text(encoding="utf-8")

@app.get("/admin", response_class=HTMLResponse)
def admin_page():
    return (APP_DIR / "admin.html").read_text(encoding="utf-8")

@app.post("/api/admin/login")
async def admin_login(payload: dict):
    supplied = str(payload.get("password", ""))
    if not ADMIN_PASSWORD or not hmac.compare_digest(supplied, ADMIN_PASSWORD):
        event("unknown", "admin_login_failed")
        raise HTTPException(401, "رمز مدیریت اشتباه است")
    token = secrets.token_urlsafe(32)
    admin_tokens.add(token)
    event("admin", "login")
    return {"token": token}

@app.get("/api/admin/overview")
def admin_overview(x_admin_token: str = Header(default="")):
    require_admin(x_admin_token)
    with db() as c:
        users = c.execute("SELECT COUNT(*) n FROM users").fetchone()["n"]
        blocked = c.execute("SELECT COUNT(*) n FROM users WHERE blocked=1").fetchone()["n"]
        recent = c.execute("SELECT * FROM users ORDER BY last_seen DESC LIMIT 100").fetchall()
        events = c.execute("SELECT * FROM events ORDER BY id DESC LIMIT 50").fetchall()
    return {"users": users, "blocked": blocked, "active_jobs": sum(1 for j in jobs.values() if j["status"] == "processing"),
            "people": [dict(x) for x in recent], "events": [dict(x) for x in events]}

@app.post("/api/admin/user/{user_id}/block")
def block_user(user_id: str, x_admin_token: str = Header(default="")):
    require_admin(x_admin_token)
    with db() as c:
        cur = c.execute("UPDATE users SET blocked=1 WHERE user_id=?", (user_id,))
        if cur.rowcount == 0: raise HTTPException(404, "کاربر پیدا نشد")
    event("admin", "block_user", user_id)
    return {"ok": True}

@app.post("/api/admin/user/{user_id}/unblock")
def unblock_user(user_id: str, x_admin_token: str = Header(default="")):
    require_admin(x_admin_token)
    with db() as c:
        cur = c.execute("UPDATE users SET blocked=0 WHERE user_id=?", (user_id,))
        if cur.rowcount == 0: raise HTTPException(404, "کاربر پیدا نشد")
    event("admin", "unblock_user", user_id)
    return {"ok": True}

@app.post("/api/transcribe")
async def transcribe(request: Request, background: BackgroundTasks, audio: UploadFile = File(...),
                     x_client_id: str = Header(default="")):
    if not GROQ_KEY:
        raise HTTPException(503, "موتور پردازش هنوز روی سرور پیکربندی نشده است")
    ip = request.client.host if request.client else "unknown"
    uid = user_check(x_client_id, ip)
    if sum(1 for j in jobs.values() if j["user_id"] == uid and j["status"] == "processing") >= MAX_ACTIVE_PER_USER:
        raise HTTPException(429, "دو پردازش هم‌زمان برای هر کاربر مجاز است")
    allowed = {".mp3",".wav",".m4a",".mp4",".mpeg",".mpga",".ogg",".webm",".flac"}
    ext = Path(audio.filename or "").suffix.lower()
    if ext not in allowed: raise HTTPException(415, "فرمت صوتی پشتیبانی نمی‌شود")
    job_id = uuid.uuid4().hex
    target = DATA / f"{job_id}{ext}"
    size = 0
    with target.open("wb") as out:
        while True:
            chunk = await audio.read(1024 * 1024)
            if not chunk: break
            size += len(chunk)
            if size > MAX_MB * 1024 * 1024:
                target.unlink(missing_ok=True)
                raise HTTPException(413, f"حداکثر حجم فایل {MAX_MB} مگابایت است")
            out.write(chunk)
    jobs[job_id] = {"user_id": uid, "status":"queued", "progress":0, "text":"", "error":None}
    background.add_task(process_audio, job_id, target)
    return {"job_id": job_id}

async def process_audio(job_id: str, source: Path):
    job = jobs[job_id]; job["status"] = "processing"
    work = DATA / f"work-{job_id}"
    work.mkdir(exist_ok=True)
    try:
        pattern = str(work / "part-%03d.mp3")
        proc = await asyncio.create_subprocess_exec(
            "ffmpeg","-y","-i",str(source),"-vn","-ac","1","-ar","16000",
            "-f","segment","-segment_time","480","-reset_timestamps","1",
            "-c:a","libmp3lame","-b:a","64k",pattern,
            stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE)
        _, err = await proc.communicate()
        if proc.returncode != 0: raise RuntimeError("تبدیل فایل صوتی ناموفق بود: " + err.decode(errors="ignore")[-500:])
        parts = sorted(work.glob("part-*.mp3"))
        if not parts: raise RuntimeError("فایل صوتی قابل پردازش نیست")
        texts = []
        async with httpx.AsyncClient(timeout=600) as client:
            for i, part in enumerate(parts):
                with part.open("rb") as fh:
                    resp = await client.post(
                        "https://api.groq.com/openai/v1/audio/transcriptions",
                        headers={"Authorization":f"Bearer {GROQ_KEY}"},
                        data={"model":MODEL,"language":"fa","response_format":"json",
                              "prompt":"متن را دقیق و به زبان فارسی بنویس. نام‌ها، اعداد و اصطلاحات را حفظ کن."},
                        files={"file":(part.name,fh,"audio/mpeg")})
                if resp.status_code >= 400:
                    raise RuntimeError(f"خطای موتور تبدیل ({resp.status_code}): {resp.text[:250]}")
                texts.append(resp.json().get("text","").strip())
                job["progress"] = int((i+1)/len(parts)*100)
        job["text"] = "\n\n".join(t for t in texts if t)
        job["status"] = "done"
        event(job["user_id"], "transcription_done", job_id, f"{len(parts)} chunks")
    except Exception as exc:
        job["status"] = "error"; job["error"] = str(exc)[:500]
        event(job["user_id"], "transcription_error", job_id, str(exc)[:200])
    finally:
        source.unlink(missing_ok=True)
        for p in work.glob("*"): p.unlink(missing_ok=True)
        work.rmdir()

@app.get("/api/status/{job_id}")
def job_status(job_id: str, x_client_id: str = Header(default="")):
    job = jobs.get(job_id)
    if not job or job["user_id"] != x_client_id: raise HTTPException(404, "پردازش پیدا نشد")
    return {k:job[k] for k in ("status","progress","text","error")}

@app.get("/api/result/{job_id}")
def job_result(job_id: str, x_client_id: str = Header(default="")):
    job = jobs.get(job_id)
    if not job or job["user_id"] != x_client_id: raise HTTPException(404, "پردازش پیدا نشد")
    if job["status"] != "done": raise HTTPException(409, "متن هنوز آماده نیست")
    return PlainTextResponse(job["text"])
