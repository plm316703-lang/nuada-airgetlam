# step12_replay_ws.py
# 목적: step10 재생기의 메시지를 화면 출력 대신 WebSocket으로 Spring 관문에 보낸다.
#      연결은 한 번만 열고, 그 안에서 시행 10개를 1초 간격으로 보낸 뒤 정상 종료한다.

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

import mne
mne.set_log_level("WARNING")

import json
import os
import time
from datetime import datetime
from pathlib import Path

import joblib
from moabb.datasets import BNCI2014_001
from moabb.paradigms import LeftRightImagery
from websockets.sync.client import connect
from websockets.exceptions import InvalidStatus, ConnectionClosed

URL = "ws://localhost:8080/ws/classifier"
N_TRIALS = 10
INTERVAL_S = 1.0

# ── ① 키 읽기 (step12_connect_test와 같음) ──────────────
api_key = os.environ.get("GATEWAY_API_KEY")
if not api_key:
    raise SystemExit("환경 변수 GATEWAY_API_KEY가 없습니다. 실행 구성에 넣어 주세요.")

# ── 모델 + 데이터 준비 (step10과 같음) ──────────────────
MODEL_DIR = Path(__file__).resolve().parent.parent / "models"
model = joblib.load(MODEL_DIR / "model_s3.joblib")
meta = json.loads((MODEL_DIR / "model_s3.json").read_text(encoding="utf-8"))
threshold = meta["threshold"]
subject = meta["subject"]

dataset = BNCI2014_001()
paradigm = LeftRightImagery()
X, y, info = paradigm.get_data(dataset=dataset, subjects=[subject])
test_mask = (info["session"] == "1test").to_numpy()
X_test, y_test = X[test_mask], y[test_mask]

pre = meta["preprocess"]
assert X_test.shape[1] == pre["n_channels"], "채널 수가 학습 때와 다름"
assert X_test.shape[2] == pre["n_times"], "시간 점 수가 학습 때와 다름"

# ── ② 연결을 한 번 열고, 그 안에서 반복 ─────────────────
try:
    with connect(URL, additional_headers={"X-API-Key": api_key}) as ws:
        print(f"연결 성공: 피험자 {subject}, 기준값 {threshold}, {N_TRIALS}개 시행\n")

        for seq in range(N_TRIALS):                       # ← 반복문 전체가 with 안에 있어야 함
            trial = X_test[seq:seq + 1]
            proba = model.predict_proba(trial)[0]
            best = int(proba.argmax())
            confidence = float(proba[best])
            decision = "MOVE" if confidence >= threshold else "ABSTAIN"

            message = {
                "version": 1,
                "type": "classification",
                "seq": seq,
                "timestamp": datetime.now().isoformat(timespec="milliseconds"),
                "subject": subject,
                "intent": model.classes_[best],
                "confidence": round(confidence, 3),
                "threshold": threshold,
                "decision": decision,
                "true_label": y_test[seq],
            }

            # ── ③ 보내기 + 화면에도 출력 (보낸 것과 받은 것을 비교하려고) ──
            text = json.dumps(message, ensure_ascii=False)
            ws.send(text)
            print("보냄:", text)

            time.sleep(INTERVAL_S)

    print("\n연결 종료 (정상)")                              # ← with 밖 = 다 보낸 뒤 1번만

except InvalidStatus as e:
    print("연결 거절: HTTP", e.response.status_code)
except ConnectionRefusedError:
    print("연결 실패: Spring이 실행 중인지 확인하세요")
except ConnectionClosed as e:
    # 보내는 도중에 Spring이 꺼지거나 연결이 끊긴 경우
    print("보내는 중 연결이 끊김:", e)