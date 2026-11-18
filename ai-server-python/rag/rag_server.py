# rag_server.py — 장비 오류 점검 안내 RAG 서버 (FastAPI, 기본 포트 8000)
#
#   [Spring 관문] ──POST /guide (오류 종류 + 상세 + 오류 이력)──▶ [이 서버]
#        1) 매뉴얼(manuals/*.md)을 Gemini 임베딩으로 Chroma에 저장해 둠 (서버 시작 때 한 번)
#        2) 질문과 비슷한 매뉴얼 조각을 찾음 (같은 오류 종류 우선)
#        3) Gemini에게 "찾은 근거와 오류 이력만으로 점검 안내를 써라"고 부탁
#   ◀── 안내문 + 근거 목록 + 근거가 충분한지
#
# 원칙 (프로젝트 결정): RAG는 안내만 한다. 로봇팔 재개·상태 변경 기능은 이 서버에 없다.
#
# 실행 (ai-server-python 폴더에서):  python rag/rag_server.py
# 필요한 환경 변수: GEMINI_API_KEY (저장소 최상위 .env)

import hashlib
import os
import re
import threading
from contextlib import asynccontextmanager
from pathlib import Path

import chromadb
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent                                  # 저장소 최상위

try:
    from dotenv import load_dotenv
    load_dotenv(ROOT / ".env", override=False)             # Docker 환경 변수가 있으면 그쪽이 우선
except ImportError:
    pass

MANUAL_DIR = HERE / "manuals"
DB_PATH = Path(os.environ.get("RAG_DB_PATH", HERE / ".chroma"))
EMBED_MODEL = os.environ.get("GEMINI_EMBED_MODEL", "gemini-embedding-001")
GEN_MODEL = os.environ.get("GEMINI_MODEL", "gemini-3.8-flash")
MIN_SCORE = float(os.environ.get("RAG_MIN_SCORE", "0.55"))   # 이 유사도 이상인 같은 종류 근거가 있어야 "근거 충분"
TOP_K = 4

SYSTEM_RULES = """너는 뇌파(EEG) 측정 장비의 점검을 돕는 안내자다. 반드시 지킬 규칙:
1. 아래 [근거]와 [오류 이력]에 있는 내용만 사용한다. 근거에 없는 점검 방법이나 수치를 지어내지 않는다.
2. 안내만 한다. 로봇팔을 재개·이동·초기화하라고 지시하거나 그렇게 했다고 말하지 않는다.
   재개는 "조건이 충족되면 관제 담당자가 관제 화면에서 수동 재개를 요청한다"로만 표현한다.
3. 의학적 진단이나 치료 판단을 하지 않는다.
4. 한국어로, 관제 담당자가 바로 따라 할 수 있게 번호 목록으로 짧게 쓴다 (8줄 이내).
5. 각 단계 끝에 근거 번호를 [1]처럼 붙인다. 오류 이력에서 온 내용은 [이력]이라고 붙인다.
6. 근거가 부족하면 첫 줄에 "근거 부족: 매뉴얼을 직접 확인하세요"라고 쓴다."""


# ─────────────────────────── 요청 / 응답 모양 ───────────────────────────
# Spring(RagClient)과 칸 이름을 똑같이 맞춘다 (camelCase)

class HistoryItem(BaseModel):
    id: int | None = None
    occurredAt: str | None = None
    resolvedAt: str | None = None
    occurrenceCount: int = 1
    detail: str | None = None


class GuideRequest(BaseModel):
    errorType: str = Field(min_length=1, max_length=40)
    detail: str | None = Field(default=None, max_length=500)
    signalQuality: float | None = None
    injected: bool = False
    history: list[HistoryItem] = Field(default_factory=list, max_length=10)


class Source(BaseModel):
    doc: str
    section: str
    score: float | None


class GuideResponse(BaseModel):
    answer: str
    sources: list[Source]
    evidenceSufficient: bool
    model: str


# ─────────────────────────── 매뉴얼 → 조각 ───────────────────────────

def load_chunks():
    """매뉴얼 파일을 '## 소제목' 단위로 자른다. 조각 = (id, 본문, 메타데이터)"""
    chunks = []
    for path in sorted(MANUAL_DIR.glob("*.md")):
        error_type = path.stem
        text = path.read_text(encoding="utf-8")
        title = text.splitlines()[0].lstrip("# ").strip()
        for part in re.split(r"\n(?=## )", text):
            if not part.startswith("## "):
                continue
            section = part.splitlines()[0][3:].strip()
            body = f"{title}\n{part.strip()}"
            chunks.append((f"{error_type}#{section}", body,
                           {"error_type": error_type, "doc": path.name, "section": section}))
    return chunks


def manuals_fingerprint(chunks):
    """매뉴얼 내용이 바뀌면 지문이 바뀜 → 새 컬렉션으로 다시 임베딩"""
    h = hashlib.sha256(EMBED_MODEL.encode())
    for cid, body, _ in chunks:
        h.update(cid.encode())
        h.update(body.encode())
    return h.hexdigest()[:12]


# ─────────────────────────── Gemini + Chroma ───────────────────────────

class Rag:
    def __init__(self):
        self.lock = threading.Lock()
        self.client = None
        self.collection = None
        self.error = None

    def ready(self):
        return self.collection is not None

    def init(self):
        """임베딩 색인 만들기. 키가 없거나 실패하면 이유를 남기고, /guide가 503으로 알려 준다"""
        with self.lock:
            if self.collection is not None:
                return
            api_key = os.environ.get("GEMINI_API_KEY")
            if not api_key:
                self.error = "GEMINI_API_KEY가 없습니다. 저장소 최상위 .env 에 넣고 RAG 서버를 다시 시작하세요."
                return
            try:
                from google import genai
                self.client = genai.Client(api_key=api_key)
                chunks = load_chunks()
                if not chunks:
                    raise RuntimeError(f"매뉴얼이 없습니다: {MANUAL_DIR}")
                name = "manuals_" + manuals_fingerprint(chunks)
                db = chromadb.PersistentClient(path=str(DB_PATH))
                col = db.get_or_create_collection(name=name, embedding_function=None,
                                                  configuration={"hnsw": {"space": "cosine"}})
                if col.count() != len(chunks):
                    ids = [c[0] for c in chunks]
                    docs = [c[1] for c in chunks]
                    metas = [c[2] for c in chunks]
                    col.upsert(ids=ids, documents=docs, metadatas=metas,
                               embeddings=self.embed(docs, "RETRIEVAL_DOCUMENT"))
                    print(f"매뉴얼 색인 완료: 조각 {len(chunks)}개 → {name}", flush=True)
                else:
                    print(f"기존 색인 사용: {name} ({col.count()}개)", flush=True)
                self.collection = col
                self.error = None
            except Exception as e:     # 키 오류, 모델 이름 오류, 네트워크 등
                self.error = f"RAG 초기화 실패: {type(e).__name__}: {e}"
                print(self.error, flush=True)

    def embed(self, texts, task_type):
        from google.genai import types
        res = self.client.models.embed_content(
            model=EMBED_MODEL, contents=texts, config=types.EmbedContentConfig(task_type=task_type))
        return [e.values for e in res.embeddings]

    def retrieve(self, req: GuideRequest):
        query = f"{req.errorType} 오류. {req.detail or ''} 원인 확인 방법 해결 단계"
        qvec = self.embed([query], "RETRIEVAL_QUERY")[0]
        found = {}
        # 같은 오류 종류 매뉴얼에서 먼저, 그다음 전체에서 보충 (원인이 겹칠 수 있음)
        for where, k in (({"error_type": req.errorType}, TOP_K), (None, 2)):
            res = self.collection.query(query_embeddings=[qvec], n_results=k, where=where)
            for cid, doc, meta, dist in zip(res["ids"][0], res["documents"][0],
                                            res["metadatas"][0], res["distances"][0]):
                if cid not in found:
                    found[cid] = (doc, meta, 1.0 - float(dist))   # 코사인 거리 → 유사도
        return sorted(found.values(), key=lambda x: -x[2])[:TOP_K + 1]

    def generate(self, req: GuideRequest, hits):
        from google.genai import types
        evidence = "\n\n".join(f"[{i}] ({m['doc']} › {m['section']})\n{doc}"
                               for i, (doc, m, _) in enumerate(hits, start=1))
        history = "\n".join(f"- 오류 #{h.id}: {h.occurrenceCount}회, 발생 {h.occurredAt}, 해결 {h.resolvedAt}, {h.detail or ''}"
                            for h in req.history) or "(같은 종류로 해결된 과거 기록 없음)"
        prompt = (f"[현재 오류]\n종류: {req.errorType}\n상세: {req.detail or '-'}\n"
                  f"신호 품질: {req.signalQuality if req.signalQuality is not None else '-'}\n"
                  f"시연용 주입 여부: {'예' if req.injected else '아니오'}\n\n"
                  f"[근거]\n{evidence}\n\n[오류 이력]\n{history}\n\n"
                  "위 규칙에 따라 관제 담당자를 위한 점검 안내를 써라.")
        res = self.client.models.generate_content(
            model=GEN_MODEL, contents=prompt,
            config=types.GenerateContentConfig(system_instruction=SYSTEM_RULES, temperature=0.2))
        return (res.text or "").strip()


rag = Rag()


@asynccontextmanager
async def lifespan(_app):
    rag.init()          # 서버 시작 때 매뉴얼 색인 (실패해도 서버는 뜨고 /health 로 이유를 보여 줌)
    yield


app = FastAPI(title="Nuada RAG — 장비 오류 점검 안내", lifespan=lifespan)


@app.get("/health")
def health():
    return {"ready": rag.ready(), "error": rag.error, "embedModel": EMBED_MODEL, "model": GEN_MODEL,
            "manuals": len(list(MANUAL_DIR.glob("*.md")))}


@app.post("/guide", response_model=GuideResponse)
def guide(req: GuideRequest):
    if not rag.ready():
        rag.init()                                   # 키를 나중에 넣은 경우 한 번 더 시도
    if not rag.ready():
        raise HTTPException(status_code=503, detail=rag.error)
    try:
        hits = rag.retrieve(req)
        same_type = [s for _, m, s in hits if m["error_type"] == req.errorType]
        sufficient = bool(same_type) and max(same_type) >= MIN_SCORE
        answer = rag.generate(req, hits)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Gemini 호출 실패: {type(e).__name__}: {e}")
    if not answer:
        raise HTTPException(status_code=502, detail="Gemini가 빈 답을 돌려줌")
    if not sufficient and not answer.startswith("근거 부족"):
        answer = "근거 부족: 매뉴얼을 직접 확인하세요\n" + answer
    sources = [Source(doc=m["doc"], section=m["section"], score=round(s, 3)) for _, m, s in hits]
    return GuideResponse(answer=answer, sources=sources, evidenceSufficient=sufficient, model=GEN_MODEL)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=os.environ.get("RAG_HOST", "127.0.0.1"), port=int(os.environ.get("RAG_PORT", "8000")))
