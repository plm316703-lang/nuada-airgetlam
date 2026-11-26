# compare.py — 직접 구현(rag/rag_server.py)과 LangChain 구현(lc_rag.py)을 같은 입력으로 비교해 results.md 를 만든다
#
#   1) 자르기: 조각 수·본문·꼬리표가 같은가
#   2) 검색 일치: 오류 8종을 /guide 와 같은 방식으로 물었을 때 같은 근거가 같은 순서로 나오는가
#   3) 미니 평가 10문항(eval_set.json): 증상만 보고 물었을 때 정답 매뉴얼·소제목을 찾는가 (오류 종류 필터 없이)
#   4) 생성: 같은 오류 3건으로 안내문을 만들고 시간·형식 규칙을 비교
#   5) 코드 줄 수
#
# 실행 (저장소 최상위에서, Gemini API를 약 70번 부름 — 임베딩이 대부분, 생성은 6번):
#   ai-server-python\rag_langchain\.venv\Scripts\python.exe ai-server-python\rag_langchain\compare.py

import json
import re
import sys
import time
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))

from rag import rag_server as base                                   # noqa: E402  직접 구현
from lc_rag import GEN_MODEL, LcRag, load_documents                   # noqa: E402  LangChain 구현

# /guide 에 실제로 들어오는 모양을 흉내 낸 오류 8종 (상세 문구는 관문 기록 형식을 참고해 만든 예시)
CASES = [
    ("ELECTRODE_CONTACT", "quality=0.42 bad_channels=C3,Cz,C4"),
    ("LINE_NOISE", "quality=0.31"),
    ("MUSCLE_ARTIFACT", "quality=0.45"),
    ("PACKET_LOSS", "no message for 3000ms"),
    ("STALE_COMMAND", "age=2713ms limit=2000ms"),
    ("INVALID_MESSAGE", "INVALID_FIELD: confidence missing"),
    ("DECISION_MISMATCH", "classifier=MOVE gate=ABSTAIN confidence=0.71"),
    ("SIGNAL_QUALITY_LOW", "quality=0.52"),
]
GEN_CASES = ["LINE_NOISE", "PACKET_LOSS", "STALE_COMMAND"]   # 생성은 비용·시간이 커서 3건만


def code_lines(path, start=None, end=None):
    """주석·빈 줄을 뺀 코드 줄 수 (start~end 표시 사이만 셀 수 있음)"""
    lines = path.read_text(encoding="utf-8").splitlines()
    if start:
        lines = lines[next(i for i, l in enumerate(lines) if start in l):]
    if end:
        lines = lines[:next(i for i, l in enumerate(lines) if end in l)]
    return sum(1 for l in lines if l.strip() and not l.strip().startswith("#"))


def answer_checks(text):
    """SYSTEM_RULES 중 기계로 확인할 수 있는 것만: 9번 '특징:' 첫 줄, 5번 근거 번호, 4번 10줄 이내"""
    lines = [l for l in text.splitlines() if l.strip()]
    return {
        "특징 첫 줄": bool(lines) and lines[0].lstrip().startswith("특징:"),
        "근거 번호": bool(re.search(r"\[\d+\]", text)),
        "10줄 이내": len([l for l in lines if re.match(r"\s*\d+\.", l)]) <= 10,
    }


def main():
    out = []
    w = out.append
    ok = lambda b: "✅" if b else "❌"   # noqa: E731

    print("직접 구현 색인 준비…", flush=True)
    base.rag.init()
    if not base.rag.ready():
        raise SystemExit(base.rag.error)
    print("LangChain 구현 색인 준비…", flush=True)
    lc = LcRag()

    w(f"# RAG 비교 결과 — 직접 구현 vs LangChain\n")
    w(f"> `compare.py`가 만든 파일 ({datetime.now():%Y-%m-%d %H:%M}). 생성 모델 `{GEN_MODEL}`, 임베딩 `{base.EMBED_MODEL}`.\n"
      "> 생성 결과는 실행할 때마다 조금씩 달라질 수 있다.\n")

    # ── 1) 자르기 ──
    a, b = base.load_chunks(), load_documents()
    same = len(a) == len(b) and all(c == d.id and m == d.metadata and body == d.page_content
                                    for (c, body, m), d in zip(a, b))
    w("## 1. 자르기\n")
    w("| 항목 | 직접 구현 | LangChain |\n|---|---|---|")
    w(f"| 조각 수 | {len(a)} | {len(b)} |")
    w(f"| 본문·꼬리표 완전히 같음 | {ok(same)} | |\n")

    # ── 2) 검색 일치 ──
    w("## 2. 검색 일치 (오류 8종, `/guide`와 같은 규칙: 같은 종류 4 + 전체 2)\n")
    w("| 오류 | 근거 목록·순서 같음 | 유사도 최대 차이 | 1등 근거 |\n|---|---|---|---|")
    match = 0
    for et, detail in CASES:
        h1 = base.rag.retrieve(base.GuideRequest(errorType=et, detail=detail))
        h2 = lc.retrieve({"errorType": et, "detail": detail})
        ids1 = [f"{m['doc']}›{m['section']}" for _, m, _ in h1]
        ids2 = [f"{d.metadata['doc']}›{d.metadata['section']}" for d, _ in h2]
        diff = max(abs(s1 - s2) for (_, _, s1), (_, s2) in zip(h1, h2)) if len(h1) == len(h2) else float("nan")
        match += ids1 == ids2
        w(f"| {et} | {ok(ids1 == ids2)} | {diff:.4f} | {ids1[0]} |")
    w(f"\n**{match}/{len(CASES)}건 일치.**\n")

    # ── 3) 미니 평가 ──
    qs = json.loads((HERE / "eval_set.json").read_text(encoding="utf-8"))["questions"]
    w("## 3. 미니 평가 10문항 (`eval_set.json`, 오류 종류 필터 없이 40조각 전체에서 5개)\n")
    w("정답 매뉴얼이 1등인가(@1), 3등 안인가(@3), 정답 소제목이 5개 안에 있는가(소제목@5). 정답은 Claude가 정했다.\n")
    w("| # | 질문 | 정답 | 직접 1등 | LangChain 1등 | @1 | @3 | 소제목@5 |\n|---|---|---|---|---|---|---|---|")
    score = {"base": [0, 0, 0], "lc": [0, 0, 0]}
    for q in qs:
        res = base.rag.collection.query(query_embeddings=[base.rag.embed([q["question"]], "RETRIEVAL_QUERY")[0]], n_results=5)
        r1 = [(m["doc"], m["section"]) for m in res["metadatas"][0]]
        r2 = [(d.metadata["doc"], d.metadata["section"]) for d, _ in
              lc.store.similarity_search_by_vector_with_relevance_scores(lc.embeddings.embed_query(q["question"]), k=5)]
        marks = []
        for key, r in (("base", r1), ("lc", r2)):
            docs = [d for d, _ in r]
            hits = [docs[0] == q["doc"], q["doc"] in docs[:3], (q["doc"], q["section"]) in r]
            score[key] = [s + h for s, h in zip(score[key], hits)]
            marks.append(hits)
        same_mark = "" if r1 == r2 else " (두 구현 순위 다름)"
        w(f"| {q['id']} | {q['question']} | {q['doc'].removesuffix('.md')} › {q['section']} | "
          f"{r1[0][0].removesuffix('.md')} › {r1[0][1]} | {r2[0][0].removesuffix('.md')} › {r2[0][1]}{same_mark} | "
          + " | ".join(f"{ok(x)}{ok(y)}" for x, y in zip(*marks)) + " |")
    w("\n(@1·@3·소제목@5 칸은 왼쪽 표시 = 직접 구현, 오른쪽 = LangChain)\n")
    w("| 지표 | 직접 구현 | LangChain |\n|---|---|---|")
    for i, name in enumerate(["정답 매뉴얼 1등 (@1)", "정답 매뉴얼 3등 안 (@3)", "정답 소제목 5개 안"]):
        w(f"| {name} | {score['base'][i]}/{len(qs)} | {score['lc'][i]}/{len(qs)} |")
    w("")

    # ── 4) 생성 ──
    w("## 4. 생성 (같은 오류 3건, 제한 25초·재시도 없음)\n")
    w("| 오류 | 구현 | 시간 | 근거 충분 | 특징 첫 줄 | 근거 번호 | 10줄 이내 |\n|---|---|---|---|---|---|---|")
    answers = []
    for et in GEN_CASES:
        detail = dict(CASES)[et]
        for name, run in (("직접", lambda: base.guide(base.GuideRequest(errorType=et, detail=detail)).model_dump()),
                          ("LangChain", lambda: lc.guide({"errorType": et, "detail": detail}))):
            t0 = time.perf_counter()
            res = run()
            sec = time.perf_counter() - t0
            c = answer_checks(res["answer"])
            w(f"| {et} | {name} | {sec:.1f}초 | {ok(res['evidenceSufficient'])} | "
              f"{ok(c['특징 첫 줄'])} | {ok(c['근거 번호'])} | {ok(c['10줄 이내'])} |")
            answers.append((et, name, res["answer"]))
    w("\n<details><summary>생성된 안내문 전체 보기</summary>\n")
    for et, name, text in answers:
        w(f"### {et} — {name}\n\n```text\n{text}\n```\n")
    w("</details>\n")

    # ── 5) 코드 줄 수 ──
    base_core = code_lines(Path(base.__file__), "def load_chunks", "rag = Rag()")
    lc_core = code_lines(HERE / "lc_rag.py", "def load_documents", 'if __name__ == "__main__"')
    w("## 5. 코드 줄 수 (주석·빈 줄 제외, 자르기~생성 핵심 부분만)\n")
    w(f"| 직접 구현 `rag_server.py` | LangChain `lc_rag.py` |\n|---|---|\n| {base_core}줄 | {lc_core}줄 |\n")

    (HERE / "results.md").write_text("\n".join(out), encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    main()
