# lc_rag.py — 기존 RAG(rag/rag_server.py)를 LangChain 부품으로 다시 조립한 버전 (비교·학습용)
#
#   매뉴얼 ─[MarkdownHeaderTextSplitter]→ Document 40개 ─[GoogleGenerativeAIEmbeddings]→ [Chroma]
#   오류 ──[검색 Runnable: 같은 종류 4 + 전체 2]→ 근거 ─[ChatPromptTemplate]→ [ChatGoogleGenerativeAI] → 안내문
#
# 서버(FastAPI)는 만들지 않는다 (11/26 "줄인 버전" 결정). 같은 입력에 같은 모양의 결과(dict)를 돌려주는
# guide() 함수까지만 만들고, 기존 구현과의 비교는 compare.py가 한다.
#
# 실행 (저장소 최상위에서):
#   ai-server-python\rag_langchain\.venv\Scripts\python.exe ai-server-python\rag_langchain\lc_rag.py LINE_NOISE "quality=0.31"

import hashlib
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))        # ai-server-python 폴더 → rag.rag_server 를 가져오려고

# 비교를 공정하게 하려고 설정값·규칙은 기존 서버의 것을 그대로 가져다 쓴다 (복사하면 한쪽만 바뀔 위험).
# rag_server 를 가져오는 순간 저장소 최상위 .env 도 읽힌다.
from rag.rag_server import (EMBED_MODEL, GEN_MODEL, GEN_TIMEOUT_SECONDS, MANUAL_DIR, MIN_SCORE,  # noqa: E402
                            SYSTEM_RULES, TOP_K)

from langchain_chroma import Chroma                                              # noqa: E402
from langchain_core.documents import Document                                    # noqa: E402
from langchain_core.output_parsers import StrOutputParser                        # noqa: E402
from langchain_core.prompts import ChatPromptTemplate                            # noqa: E402
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings  # noqa: E402
from langchain_text_splitters import MarkdownHeaderTextSplitter                  # noqa: E402

# 기존 색인(rag/.chroma)과 섞이지 않게 다른 폴더. .gitignore 의 ".chroma/" 규칙에 걸려 커밋되지 않는다
DB_PATH = HERE / ".chroma"


# ─────────────────────────── ①② 자르기 → Document ───────────────────────────

def load_documents():
    """매뉴얼을 '## 소제목' 단위 Document로 자른다. 기존 load_chunks()와 같은 40개·같은 꼬리표가 나와야 한다"""
    # strip_headers=False: 소제목 줄("## 원인")을 본문에 남긴다. 기존 구현도 소제목을 본문에 넣어 임베딩했으므로
    splitter = MarkdownHeaderTextSplitter(headers_to_split_on=[("#", "title"), ("##", "section")],
                                          strip_headers=False)
    docs = []
    for path in sorted(MANUAL_DIR.glob("*.md")):
        for part in splitter.split_text(path.read_text(encoding="utf-8")):
            if "section" not in part.metadata:
                continue                     # 제목·참고 자료 머리말은 기존처럼 버린다 (점검 내용이 아님)
            section = part.metadata["section"]
            docs.append(Document(
                id=f"{path.stem}#{section}",
                # 기존처럼 본문 앞에 매뉴얼 제목을 붙인다: "## 원인"만으로는 어느 오류의 원인인지 임베딩이 알 수 없음
                page_content=f"{part.metadata['title']}\n{part.page_content.strip()}",
                metadata={"error_type": path.stem, "doc": path.name, "section": section},
            ))
    return docs


def fingerprint(docs):
    """매뉴얼이나 임베딩 모델이 바뀌면 이름이 바뀌어 새 컬렉션으로 다시 색인한다 (기존과 같은 방식)"""
    h = hashlib.sha256(EMBED_MODEL.encode())
    for d in docs:
        h.update(d.id.encode())
        h.update(d.page_content.encode())
    return h.hexdigest()[:12]


# ─────────────────────────── ③④ 임베딩 → 벡터 저장소 ───────────────────────────

def api_key():
    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        raise RuntimeError("GEMINI_API_KEY가 없습니다. 저장소 최상위 .env 를 확인하세요.")
    return key


def build_store():
    # embed_documents()는 RETRIEVAL_DOCUMENT, embed_query()는 RETRIEVAL_QUERY 를 기본으로 쓴다
    # → 기존 embed(texts, task_type)의 문서용·질문용 구분이 메서드 두 개로 나뉘어 있는 셈
    embeddings = GoogleGenerativeAIEmbeddings(model=EMBED_MODEL, google_api_key=api_key())
    docs = load_documents()
    store = Chroma(
        collection_name="lc_manuals_" + fingerprint(docs),
        embedding_function=embeddings,
        persist_directory=str(DB_PATH),
        # 기존과 같은 코사인 거리. 기본값(l2)으로 두면 점수 크기가 달라져 0.55 기준을 그대로 쓸 수 없다
        collection_configuration={"hnsw": {"space": "cosine"}},
    )
    if len(store.get(include=[])["ids"]) != len(docs):
        store.add_documents(docs, ids=[d.id for d in docs])
    return store, embeddings, len(docs)


# ─────────────────────────── ⑤ 검색 ───────────────────────────

def make_retriever(store, embeddings):
    """기존 retrieve()와 같은 규칙: 같은 오류 종류에서 4개 + 전체에서 2개 보충 → 중복 제거 → 유사도순 최대 5개.

    store.as_retriever()는 "비슷한 것 k개"만 찾을 수 있어서 이 규칙을 담지 못한다.
    그래서 함수로 직접 만들고, 체인에 끼울 수 있게 호출만 LangChain 모양(dict 입력)으로 맞춘다.
    """
    def retrieve(req):
        query = f"{req['errorType']} 오류. {req.get('detail') or ''} 원인 확인 방법 해결 단계"
        qvec = embeddings.embed_query(query)               # 질문 임베딩은 한 번만 (두 번 검색에 같이 씀)
        found = {}
        for flt, k in (({"error_type": req["errorType"]}, TOP_K), (None, 2)):
            # 이 함수의 둘째 값은 이름과 달리 "거리"다 (작을수록 비슷함) → 기존처럼 1 - 거리 = 유사도
            for doc, dist in store.similarity_search_by_vector_with_relevance_scores(qvec, k=k, filter=flt):
                found.setdefault(doc.id, (doc, 1.0 - float(dist)))
        return sorted(found.values(), key=lambda x: -x[1])[:TOP_K + 1]
    return retrieve


# ─────────────────────────── ⑥⑦⑧ 프롬프트 → 생성 모델 → 체인 ───────────────────────────

# 사람 쪽 메시지는 기존 generate()의 문자열과 같은 순서·같은 문구. 빈칸은 {이름}으로 표시한다
PROMPT = ChatPromptTemplate.from_messages([
    ("system", SYSTEM_RULES),
    ("human", "[현재 오류]\n종류: {error_type}\n상세: {detail}\n신호 품질: {quality}\n시연용 주입 여부: {injected}\n\n"
              "[근거]\n{evidence}\n\n[오류 이력]\n{history}\n\n위 규칙에 따라 관제 담당자를 위한 점검 안내를 써라."),
])


def make_chain():
    llm = ChatGoogleGenerativeAI(
        model=GEN_MODEL,
        temperature=0.2,
        google_api_key=api_key(),
        timeout=GEN_TIMEOUT_SECONDS,   # 이 라이브러리는 초 단위 (안에서 밀리초로 바꿔 넘긴다)
        # 기본값이 6(재시도 포함 최대 6번 시도)이다. 0은 "구글 기본값(5번 재시도)"으로 해석되므로 1이 "재시도 없음".
        # 재시도가 켜져 있으면 25초 x 시도 횟수가 되어 Spring의 40초 제한을 넘는다
        max_retries=1,
    )
    return PROMPT | llm | StrOutputParser()    # | : 앞 부품의 출력이 뒤 부품의 입력이 된다


def prompt_inputs(req, hits):
    evidence = "\n\n".join(f"[{i}] ({d.metadata['doc']} › {d.metadata['section']})\n{d.page_content}"
                           for i, (d, _) in enumerate(hits, start=1))
    history = "\n".join(f"- 오류 #{h.get('id')}: {h.get('occurrenceCount', 1)}회, 발생 {h.get('occurredAt')}, "
                        f"해결 {h.get('resolvedAt')}, {h.get('detail') or ''}"
                        for h in req.get("history", [])) or "(같은 종류로 해결된 과거 기록 없음)"
    q = req.get("signalQuality")
    return {"error_type": req["errorType"], "detail": req.get("detail") or "-",
            "quality": q if q is not None else "-", "injected": "예" if req.get("injected") else "아니오",
            "evidence": evidence, "history": history}


class LcRag:
    def __init__(self):
        self.store, self.embeddings, self.count = build_store()
        self.retrieve = make_retriever(self.store, self.embeddings)
        self.chain = make_chain()

    def guide(self, req):
        """기존 /guide 응답과 같은 모양(answer, sources, evidenceSufficient, model)을 dict로 돌려준다"""
        hits = self.retrieve(req)              # 검색 실패는 그대로 예외 (기존: 502)
        same_type = [s for d, s in hits if d.metadata["error_type"] == req["errorType"]]
        sufficient = bool(same_type) and max(same_type) >= MIN_SCORE
        sources = [{"doc": d.metadata["doc"], "section": d.metadata["section"], "score": round(s, 3)} for d, s in hits]
        try:
            answer = self.chain.invoke(prompt_inputs(req, hits)).strip()
        except Exception as e:                 # 생성이 실패해도 찾은 근거는 돌려준다 (기존과 같은 우아한 성능 저하)
            print(f"안내문 생성 실패 (제한 {GEN_TIMEOUT_SECONDS:.0f}초): {type(e).__name__}: {e}", flush=True)
            answer = ""
        if not answer:
            answer = "안내문 생성 실패: AI 응답이 늦거나 오류가 났습니다.\n아래 근거 매뉴얼을 직접 확인하세요."
        elif not sufficient and not answer.startswith("근거 부족"):
            answer = "근거 부족: 매뉴얼을 직접 확인하세요\n" + answer
        return {"answer": answer, "sources": sources, "evidenceSufficient": sufficient, "model": GEN_MODEL}


if __name__ == "__main__":
    error_type = sys.argv[1] if len(sys.argv) > 1 else "LINE_NOISE"
    detail = sys.argv[2] if len(sys.argv) > 2 else None
    rag = LcRag()
    print(f"색인: 조각 {rag.count}개 ({DB_PATH})")
    t0 = time.perf_counter()
    res = rag.guide({"errorType": error_type, "detail": detail})
    print(f"\n[{time.perf_counter() - t0:.1f}초] 근거 충분: {res['evidenceSufficient']}  모델: {res['model']}\n")
    print(res["answer"])
    print("\n근거:")
    for s in res["sources"]:
        print(f"  {s['doc']} › {s['section']} (유사도 {s['score']})")
