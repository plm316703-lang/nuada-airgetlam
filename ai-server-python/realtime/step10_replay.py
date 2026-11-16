# ai-server-python/realtime/step10_replay.py
# 목적: 저장한 판별기(model_s3)를 불러와, 평가 세션 시행을 1초에 하나씩 "실시간처럼" 재생한다.
#      시행마다 판별 결과를 메시지(dict → JSON 문자열)로 만들어 화면에 출력한다.
#      ※ 아직 네트워크로 보내지 않는다. 메시지가 제대로 만들어지는지만 확인한다.
#      ※ 단순화: 진짜 연속 뇌파가 아니라, 이미 잘라 둔 시행을 하나씩 재생한다.

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

import mne
mne.set_log_level("WARNING")

import json
import time
from datetime import datetime
from pathlib import Path

import joblib
from moabb.datasets import BNCI2014_001
from moabb.paradigms import LeftRightImagery

N_TRIALS = 10 #재생할 시행 수(확인용으로 조금만)
INTERVAL_S = 1.0 #시행 사이 간격(초)

# ── 1) 모델 + 라벨(json) 불러오기 ───────────────────────
MODEL_DIR = Path(__file__).resolve().parent.parent / "models"
model = joblib.load(MODEL_DIR / "model_s3.joblib")        # 직접 만든 파일만 불러온다
meta = json.loads((MODEL_DIR / "model_s3.json").read_text(encoding="utf-8"))
threshold = meta["threshold"]                              # 0.75
subject = meta["subject"]                                  # 3

# ── 2) 재생할 데이터 준비: 평가 세션(1test)만 ───────────
dataset = BNCI2014_001()
paradigm = LeftRightImagery()
X, y, info = paradigm.get_data(dataset=dataset, subjects=[subject])
test_mask = (info["session"] == "1test").to_numpy()
X_test, y_test = X[test_mask], y[test_mask]

# 안전 확인: 학습할 때와 같은 모양인지 (다르면 판별기가 조용히 틀릴 수 있다)
pre = meta["preprocess"]
assert X_test.shape[1] == pre["n_channels"], "채널 수가 학습 때와 다름"
assert X_test.shape[2] == pre["n_times"], "시간 점 수가 학습 때와 다름"

# ── 3) 시행을 하나씩 재생 ─────────────────────────────
print(f"재생 시작: 피험자 {subject}, 기준값 {threshold}, {N_TRIALS}개 시행\n")

for seq in range(N_TRIALS):
    trial = X_test[seq:seq + 1]     #모양 (1, 22, 1001) 판별기
    proba = model.predict_proba(trial)[0]       #[왼손 확률, 오른손 확률]
    best = int(proba.argmax())
    confidence = float(proba[best])


    #판단 : 기준값 이상이면 움직임(MOVE), 미만이면 보류 (ABSTAIN)
    decision = "MOVE" if confidence >= threshold else "ABSTAIN"

    message = {
        "type": "classification",                          # 메시지 종류 (나중에 다른 종류가 추가될 수 있음)
        "seq": seq,                                        # 순서 번호 (빠지거나 순서가 바뀐 것 확인용)
        "timestamp": datetime.now().isoformat(timespec="milliseconds"),
        "subject": subject,
        "intent": model.classes_[best],                    # 판별한 의도: left_hand / right_hand
        "confidence": round(confidence, 3),
        "threshold": threshold,
        "decision": decision,
        "true_label": y_test[seq],                         # 시연용 정답 (실제 사용에서는 알 수 없는 값)
    }

    # dict → JSON 문자열 (WebSocket으로 보낼 때도 이 모양 그대로 보낸다)
    print(json.dumps(message, ensure_ascii=False))
    time.sleep(INTERVAL_S)                                 # 다음 시행까지 기다리기

print("\n재생 끝")