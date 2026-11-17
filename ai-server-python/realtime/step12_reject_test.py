# step12_reject_test.py
# 목적: 관문(Spring)이 잘못된 메시지를 올바른 사유로 거부하는지 확인하는 시험 스크립트.
#      시험마다 "기대 결과"를 먼저 출력하고 보낸다 → Spring 콘솔과 나란히 놓고 비교.
#      한 연결 안에서 차례로 보내므로, seq 장부 상태를 계산해서 순서를 짰다.

import json
import os
import time
from datetime import datetime

from websockets.sync.client import connect
from websockets.exceptions import InvalidStatus, ConnectionClosed

URL = "ws://localhost:8080/ws/classifier"
GAP_S = 0.3   # 메시지 사이 간격: Spring 로그가 순서대로 찍히게 약간 띄움

api_key = os.environ.get("GATEWAY_API_KEY")
if not api_key:
    raise SystemExit("환경 변수 GATEWAY_API_KEY가 없습니다. 실행 구성에 넣어 주세요.")


def base(seq_no, **changes):
    """정상 메시지 하나를 만들고, changes로 일부 칸만 바꿔서 돌려준다.
    예) base(1, confidence=1.5) → confidence만 1.5인 메시지
    """
    msg = {
        "version": 1,
        "type": "classification",
        "seq": seq_no,
        "timestamp": datetime.now().isoformat(timespec="milliseconds"),
        "subject": 3,
        "intent": "left_hand",
        "confidence": 0.9,      # 기준값 0.75 이상 → 관문도 MOVE로 계산
        "threshold": 0.75,
        "decision": "MOVE",
        "true_label": "left_hand",
    }
    msg.update(changes)         # dict.update: 같은 키는 덮어쓰고, 없던 키는 추가
    return msg


def without(msg, key):
    """msg에서 key 칸을 아예 지운 복사본 (빠진 칸 시험용)"""
    copy = dict(msg)
    del copy[key]
    return copy


# ── 시험 목록: (설명, 보낼 내용, 기대 결과) ───────────────────────────
# 보낼 내용이 dict면 json.dumps로 바꾸고, str이면 그대로 보낸다 (깨진 JSON 시험용)
CASES = [
    # 장부: 없음
    ("정상 (기준선)",                    base(0),                              "판정 final=MOVE"),
    # 장부: 0
    ("깨진 JSON",                        '{"version": 1, "seq": ',             "INVALID_FORMAT"),
    ("seq 타입 틀림 (\"하나\")",          base(1, seq="하나"),                  "INVALID_FORMAT"),
    ("confidence가 NaN",                 base(1, confidence=float("nan")),     "INVALID_FORMAT"),
    ("confidence 칸 없음",               without(base(1), "confidence"),       "INVALID_FIELD"),
    ("confidence 범위 밖 (1.5)",         base(1, confidence=1.5),              "INVALID_FIELD"),
    ("decision 허용값 아님 (\"move\")",  base(1, decision="move"),             "INVALID_FIELD"),
    ("version 2 (모르는 버전)",          base(1, version=2),                   "UNSUPPORTED_VERSION"),
    # 위 거부들은 장부를 안 바꿈 → 장부 여전히 0
    ("정상 seq=1",                       base(1),                              "판정 final=MOVE"),
    # 장부: 1
    ("중복 seq=1",                       base(1),                              "OUT_OF_ORDER"),
    ("옛 메시지 seq=0",                  base(0),                              "OUT_OF_ORDER"),
    # 장부: 1 (위 거부는 장부를 안 바꿈)
    ("낮은 확신도인데 MOVE라고 주장",     base(2, confidence=0.5),              "DECISION_MISMATCH"),
    # 12-5d는 순서 검사 뒤 → 거부돼도 장부는 2로 갱신됨
    ("기준값을 0.1로 조작",              base(3, confidence=0.6, threshold=0.1), "DECISION_MISMATCH"),
    # 장부: 3
    ("번호 건너뜀 seq=10",               base(10),                             "판정 + 'seq 건너뜀 missing=6' 경고"),
]

try:
    with connect(URL, additional_headers={"X-API-Key": api_key}) as ws:
        print(f"연결 성공: 시험 {len(CASES)}개\n")

        for i, (title, payload, expected) in enumerate(CASES, start=1):
            # dict면 JSON 문자열로, 이미 문자열(깨진 JSON)이면 그대로
            text = payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False)

            print(f"[{i:02d}] {title}")
            print(f"     기대: {expected}")
            print(f"     보냄: {text}\n")
            ws.send(text)
            time.sleep(GAP_S)

    print("시험 끝 (연결 정상 종료)")

except InvalidStatus as e:
    print("연결 거절: HTTP", e.response.status_code)
except ConnectionRefusedError:
    print("연결 실패: Spring이 실행 중인지 확인하세요")
except ConnectionClosed as e:
    print("보내는 중 연결이 끊김:", e)