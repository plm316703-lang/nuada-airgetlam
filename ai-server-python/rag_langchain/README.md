# rag_langchain — 같은 RAG를 LangChain으로 다시 만들기 (학습용)

> **코드는 내가 직접 쓴다.** Claude는 설명·질문·리뷰만 한다 (`CLAUDE.md` 2-1장 "학습 모드").
> 정답지: [`../rag/rag_server.py`](../rag/rag_server.py) — LangChain 없이 직접 구현한 기존 RAG

## 목표

같은 기능(장비 오류 → 매뉴얼 근거 검색 → 점검 안내문)을 LangChain으로 만들고,
**직접 구현과 무엇이 같고 무엇이 다른지**를 숫자와 경험으로 비교한다. 성능을 올리는 것이 목적이 아니다.

## 지켜야 할 것 (기존 RAG와 공정하게 비교하려고)

| 항목 | 기존과 같게 |
|---|---|
| 매뉴얼 | `../rag/manuals/*.md` 8개 그대로 (복사하지 말고 같은 파일을 읽기) |
| 자르기 | `## 소제목` 단위, 조각 40개, 꼬리표 `error_type` · `doc` · `section` |
| 임베딩 모델 | `.env`의 `GEMINI_EMBED_MODEL` (기본 `gemini-embedding-001`), 문서용·질문용 구분 |
| 검색 | 같은 오류 종류에서 4개 + 전체에서 2개 보충, 중복 제거, 유사도 높은 순 최대 5개 |
| 생성 모델 | `.env`의 `GEMINI_MODEL`, 온도 0.2, **25초 제한, 재시도 끄기** |
| 규칙 | 기존 `SYSTEM_RULES` 그대로 |
| 응답 | `/guide` 요청·응답 모양 그대로 (`answer`, `sources`, `evidenceSufficient`, `model`) |
| 실패 처리 | 찾기 실패 502 / 쓰기 실패해도 근거 반환 / 같은 종류 유사도 0.55 미만이면 "근거 부족" |
| 저장 위치 | 기존 `.chroma`와 **다른 폴더** (서로 덮어쓰지 않게) |
| 포트 | **8001** (기존 RAG는 8000) |

## 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| A0 | 학습 모드 규칙, 브랜치, 이 폴더, 가상환경 | ✅ |
| A1 | LangChain 개념: Document · Text Splitter · Embeddings · VectorStore · Retriever · Prompt · ChatModel · 체인(`\|`) | |
| A2 | 매뉴얼 자르기 + 색인 → 조각 40개가 기존과 같은지 확인 | |
| A3 | 검색 → 같은 질문에 기존과 같은 근거가 나오는지 확인 | |
| A4 | 프롬프트 + 생성 체인 | |
| A5 | 실패 처리 + FastAPI `/guide` (8001) → Spring 주소만 바꿔 연결 | |
| A6 | 비교표: 코드 줄 수 · 응답 시간 · 근거 일치 · 답변 품질 · 느낀 점 | |

## 실행 환경

```powershell
# 처음 한 번 (Claude가 A0에서 만들어 둠)
ai-server-python\rag_langchain\.venv\Scripts\python.exe -m pip install -r ai-server-python\rag_langchain\requirements.txt

# 실행 (A5 이후)
ai-server-python\rag_langchain\.venv\Scripts\python.exe ai-server-python\rag_langchain\<내가 만든 파일>.py
```

비밀 값은 기존과 같이 저장소 최상위 `.env`에서 읽는다.
