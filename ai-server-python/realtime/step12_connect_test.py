# step12_connect_test.py
# 목적: 공유 비밀키를 들고 Spring 관문(/ws/classifier)에 연결해서,
#      메시지 1개를 보내고 정상 종료되는지만 확인한다.
#      ※ 재생기 전체 연결은 다음 단계(12-4c)에서 한다.

import json
import os

# websockets.sync.client = "기다렸다가 다음 줄로 가는" 동기 방식 클라이언트
# (비동기 async 방식도 있지만, 처음에는 위에서 아래로 읽히는 동기 방식이 이해하기 쉽다)
from websockets.sync.client import connect
from websockets.exceptions import InvalidStatus

URL = "ws://localhost:8080/ws/classifier"

# ── 1) 비밀키를 환경 변수에서 읽기 ─────────────────────
# os.environ.get("이름") = 환경 변수 값, 없으면 None
api_key = os.environ.get("GATEWAY_API_KEY")
if not api_key:
    # Spring과 같은 원칙: 키가 없으면 아예 시작하지 않는다
    raise SystemExit("환경 변수 GATEWAY_API_KEY가 없습니다. 실행 구성에 넣어 주세요.")

# ── 2) 보낼 메시지 1개 (규격 v1 모양) ───────────────────
message = {
    "version": 1,
    "type": "classification",
    "seq": 0,
    "subject": 3,
    "intent": "left_hand",
    "confidence": 0.962,
    "threshold": 0.75,
    "decision": "MOVE",
    "true_label": "left_hand",
}

# ── 3) 연결 → 보내기 → 끊기 ────────────────────────────
try:
    # additional_headers = 연결 요청에 헤더를 덧붙인다 → 문지기가 이 값을 확인
    # with 블록이 끝나면 연결을 "정상 종료(1000)"로 자동으로 닫아 준다
    with connect(URL, additional_headers={"X-API-Key": api_key}) as ws:
        print("연결 성공")
        text = json.dumps(message, ensure_ascii=False)   # dict → JSON 문자열
        ws.send(text)
        print("보냄:", text)
    print("연결 종료")

except InvalidStatus as e:
    # 문지기가 거절하면(401) 여기로 온다. 키 값은 출력하지 않는다
    print("연결 거절: HTTP", e.response.status_code)

except ConnectionRefusedError:
    # Spring이 꺼져 있으면 여기로 온다
    print("연결 실패: Spring이 실행 중인지 확인하세요")