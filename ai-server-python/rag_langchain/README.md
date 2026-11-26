# rag_langchain — 같은 RAG를 LangChain으로 다시 만들기 (비교용)

> 정답지: [`../rag/rag_server.py`](../rag/rag_server.py) — LangChain 없이 직접 구현한 기존 RAG
>
> **누가 만들었나:** 원래 계획은 "코드는 내가 직접, Claude는 리뷰만"(가이드 모드)이었다.
> 11/26 프로젝트 마무리를 Claude에게 위임하면서 **`lc_rag.py`·`compare.py`·`eval_set.json`은 Claude Code가 작성**했다
> (`CLAUDE.md` 2-1장 7번). A0(폴더·가상환경·버전 고정)과 A1 개념 정리까지가 가이드 모드 구간이다.

## 목표

같은 기능(장비 오류 → 매뉴얼 근거 검색 → 점검 안내문)을 LangChain으로 만들고,
**직접 구현과 무엇이 같고 무엇이 다른지**를 숫자로 비교한다. 성능을 올리는 것이 목적이 아니다.

## 결과 한눈에 보기 (A6 비교표)

전체 숫자는 [`results.md`](results.md) (`compare.py`가 만든 파일).

| 항목 | 직접 구현 `rag_server.py` | LangChain `lc_rag.py` | 해석 |
|---|---|---|---|
| 자르기 | 조각 40개 | 조각 40개, **본문·꼬리표까지 완전히 같음** | `MarkdownHeaderTextSplitter(strip_headers=False)` + 제목 붙이기로 같게 맞출 수 있었다 |
| 검색 일치 (오류 8종) | — | **8/8 같은 근거·같은 순서, 유사도 차이 0** | 같은 임베딩 모델·같은 Chroma·같은 규칙이면 결과도 같다 |
| 미니 평가 10문항 (필터 없이) | @1 9/10, @3 10/10 | @1 9/10, @3 10/10 | 틀린 1문항(충전기 → 전원 잡음)은 두 구현이 **똑같이** 틀림 → 품질은 프레임워크가 아니라 **자르기·임베딩·검색 규칙**이 정한다 |
| 생성 형식 규칙 (3건) | 3/3 통과 (2차 실행) | 3/3 통과 | 같은 `SYSTEM_RULES`, 같은 프롬프트 문구 |
| 생성 시간 | 1.6~14.4초 | 1.8~3.1초 | 실행마다 크게 흔들림. Gemini 서버 상태가 지배적이라 구현 차이로 볼 수 없음 |
| 코드 줄 수 (핵심 부분) | 101줄 | 99줄 | **거의 같다.** 우리 검색 규칙(같은 종류 4 + 전체 2)은 LangChain 기본 검색기에 없어 직접 짜야 했다 |

### 비교하면서 알게 된 것

1. **숨은 기본값.** `ChatGoogleGenerativeAI`의 `max_retries` 기본값은 **6**이고, `0`은 "재시도 없음"이 아니라 **구글 기본값(5번 재시도)**으로 해석된다.
   그대로 두면 25초 제한 x 여러 번 = Spring의 40초 제한을 넘는다. 직접 구현에서는 `HttpRetryOptions(attempts=1)`을 눈으로 보고 넣었지만,
   LangChain에서는 라이브러리 소스의 설명을 읽어야 알 수 있었다. → 1차 실험 회고의 "블랙박스" 문제가 프레임워크에도 그대로 있다.
2. **이름과 다른 값.** `similarity_search_by_vector_with_relevance_scores()`의 둘째 값은 이름과 달리 **거리**(작을수록 비슷함)다.
   `1 - 거리`로 바꾸지 않으면 "근거 충분" 기준(0.55)이 거꾸로 동작한다.
3. **기본 거리 방식.** Chroma 기본값은 l2 거리다. 기존과 같은 점수를 얻으려면 `collection_configuration={"hnsw": {"space": "cosine"}}`를 직접 줘야 한다.
4. **LangChain이 준 것.** 부품 이름이 표준이라 코드가 읽기 쉽고(`PROMPT | llm | StrOutputParser()`), 모델이나 벡터 저장소를 바꿀 때 한 줄만 바꾸면 된다.
   이 프로젝트처럼 부품이 하나씩뿐이고 규칙이 특수하면 줄 수 이득은 거의 없다.
5. **실패 처리가 실제로 쓰였다.** 첫 비교 실행에서 직접 구현의 생성 1건이 Gemini **503(과부하)**으로 실패했지만, 찾은 근거는 그대로 돌아왔다 (A2의 "생성 실패해도 근거 표시" 결정이 동작한 사례).

## 지켜야 할 것 (기존 RAG와 공정하게 비교하려고)

| 항목 | 기존과 같게 | 어떻게 지켰나 |
|---|---|---|
| 매뉴얼 | `../rag/manuals/*.md` 8개 그대로 | 같은 폴더(`MANUAL_DIR`)를 읽음 |
| 자르기 | `## 소제목` 단위, 조각 40개, 꼬리표 `error_type` · `doc` · `section` | `compare.py` 1번에서 완전 일치 확인 |
| 임베딩 모델 | `.env`의 `GEMINI_EMBED_MODEL`, 문서용·질문용 구분 | `embed_documents()`·`embed_query()`가 각각 `RETRIEVAL_DOCUMENT`·`RETRIEVAL_QUERY` |
| 검색 | 같은 종류 4 + 전체 2, 중복 제거, 최대 5개 | `make_retriever()` 직접 작성 |
| 생성 모델 | `.env`의 `GEMINI_MODEL`, 온도 0.2, 25초 제한, 재시도 끄기 | `timeout=25`, `max_retries=1` |
| 규칙 | 기존 `SYSTEM_RULES` 그대로 | 복사하지 않고 `rag_server`에서 가져다 씀 |
| 응답 | `answer`, `sources`, `evidenceSufficient`, `model` | `LcRag.guide()`가 같은 모양의 dict |
| 실패 처리 | 생성 실패해도 근거 반환, 0.55 미만이면 "근거 부족" | 같은 문구·같은 기준 |
| 저장 위치 | 기존 `rag/.chroma`와 다른 폴더 | `rag_langchain/.chroma` (커밋 안 됨) |

## 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| A0 | 폴더, 가상환경, 버전 고정 | ✅ PR #33 |
| A1 | LangChain 개념: Document · Text Splitter · Embeddings · VectorStore · Retriever · Prompt · ChatModel · 체인(`\|`) | ✅ 11/26 (채팅 설명) |
| A2 | 매뉴얼 자르기 + 색인 → 조각 40개가 기존과 같은지 확인 | ✅ 완전 일치 |
| A3 | 검색 → 같은 질문에 기존과 같은 근거가 나오는지 확인 | ✅ 8/8 |
| A4 | 프롬프트 + 생성 체인 | ✅ |
| A5 | FastAPI `/guide` (8001) → Spring 연결 | ➖ 11/26 "줄인 버전" 결정으로 뺌 (`guide()` 함수까지만) |
| A6 | 비교표 + 미니 평가 10문항 | ✅ 위 표, [`results.md`](results.md) |

## 실행

```powershell
# 처음 한 번
ai-server-python\rag_langchain\.venv\Scripts\python.exe -m pip install -r ai-server-python\rag_langchain\requirements.txt

# 안내문 하나 만들어 보기 (오류 종류, 상세)
ai-server-python\rag_langchain\.venv\Scripts\python.exe ai-server-python\rag_langchain\lc_rag.py LINE_NOISE "quality=0.31"

# 두 구현 비교 → results.md 다시 만들기 (Gemini API 약 70번 호출)
ai-server-python\rag_langchain\.venv\Scripts\python.exe ai-server-python\rag_langchain\compare.py
```

비밀 값은 기존과 같이 저장소 최상위 `.env`에서 읽는다. PowerShell에서 한글이 깨지면 먼저 `$env:PYTHONIOENCODING="utf-8"`.
