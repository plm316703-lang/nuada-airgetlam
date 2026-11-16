# 목적: 시연용 피험자(3)의 판별기를 학습 세션 전체로 학습하고, 기준값과 함께 파일로 저장한다.
#      저장한 파일을 다시 불러와 평가 세션 점수가 step8과 같은지 검증한다.
#      ※ 피험자 3은 전체 결과를 본 뒤 시연용으로 고름 (portfolio-log에 기록함)

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

import mne
mne.set_log_level("WARNING")

import json
from datetime import date
from pathlib import Path

import joblib                                              # 파이썬 객체를 파일로 저장/불러오기
import numpy as np
import sklearn
import moabb
from moabb.datasets import BNCI2014_001
from moabb.paradigms import LeftRightImagery
from mne.decoding import CSP
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis as LDA
from sklearn.pipeline import make_pipeline
from sklearn.model_selection import StratifiedKFold, cross_val_predict


SUBJECT = 3                                                # 시연용 피험자
SEED = 20261115
CANDIDATES = np.round(np.arange(0.50, 0.96, 0.05), 2)
MIN_ANSWER_RATE = 0.90


# 저장 위치: 이 파일 기준 ../models/  (ai-server-python/models/)
MODEL_DIR = Path(__file__).resolve().parent.parent /"models"
MODEL_DIR.mkdir(exist_ok=True)
MODEL_PATH = MODEL_DIR / f"model_s{SUBJECT}.joblib"
META_PATH  = MODEL_DIR / f"model_s{SUBJECT}.json"

# ── 1) 데이터 + 세션 분리 ───────────────────────────────
dataset = BNCI2014_001()
paradigm = LeftRightImagery()
X, y, meta = paradigm.get_data(dataset=dataset, subjects=[SUBJECT])
sessions = sorted(meta["session"].unique())
train_mask = (meta["session"] == sessions[0]).to_numpy()
test_mask  = (meta["session"] == sessions[1]).to_numpy()
X_train, y_train = X[train_mask], y[train_mask]
X_test,  y_test  = X[test_mask],  y[test_mask]

clf = make_pipeline(CSP(n_components=6, log=True), LDA())

# ── 2) 기준값 고르기 (step8과 같은 규칙, 학습 세션 안에서만) ──
cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
proba_cv = cross_val_predict(clf, X_train, y_train, cv=cv, method="predict_proba")
classes = np.unique(y_train)
conf_cv = proba_cv.max(axis=1)
correct_cv = classes[proba_cv.argmax(axis=1)] == y_train

best_t, best_acc = 0.5, -1.0
for t in CANDIDATES:                                       # 오름차순이라 같은 정확도면 낮은 값이 남는다
    answered = conf_cv >= t
    if answered.mean() >= MIN_ANSWER_RATE:
        acc = correct_cv[answered].mean()
        if acc > best_acc:                                 # '>' 이므로 같으면 먼저 나온(낮은) 값 유지
            best_t, best_acc = float(t), acc
print("고른 기준값:", best_t)

# ── 3) 학습 세션 전체로 최종 학습 ──────────────────────
clf.fit(X_train, y_train)
# ── 4) 평가 세션 점수 계산 (라벨에 적어 둘 성적) ────────
def score(model, threshold):
    """평가 세션에서 (보류 없는 정확도, 답변율, 답한 것 중 정확도)를 돌려준다."""
    proba = model.predict_proba(X_test)
    conf = proba.max(axis=1)
    correct = model.classes_[proba.argmax(axis=1)] == y_test
    answered = conf >= threshold
    return float(correct.mean()), float(answered.mean()), float(correct[answered].mean())

acc_all, rate, acc_ans = score(clf, best_t)

# ── 5) 저장: 판별기(joblib) + 라벨(json) ────────────────
joblib.dump(clf, MODEL_PATH)

metadata = {
    "subject": SUBJECT,
    "dataset": "BNCI2014_001",
    "classes": clf.classes_.tolist(),                      # 확률 열 순서 [left_hand, right_hand]
    "threshold": best_t,                                   # 이 값 미만이면 판단 보류
    "preprocess": {                                        # 서버가 똑같이 맞춰야 할 조건
        "filters_hz": paradigm.filters,                    # [[8, 32]]
        "interval_s": dataset.interval,                    # [2, 6]
        "sfreq_hz": 250,
        "n_channels": int(X.shape[1]),                     # 22
        "n_times": int(X.shape[2]),                        # 1001
    },
    "metrics_test_session": {
        "accuracy_no_abstain": round(acc_all, 3),
        "answer_rate": round(rate, 3),
        "accuracy_answered": round(acc_ans, 3),
    },
    "versions": {
        "mne": mne.__version__,
        "moabb": moabb.__version__,
        "scikit-learn": sklearn.__version__,
    },
    "trained_on": sessions[0],
    "created": str(date.today()),
}
META_PATH.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
print("저장 완료:", MODEL_PATH.name, "/", META_PATH.name)

# ── 6) 검증: 다시 불러와서 같은 점수가 나오는지 ──────────
loaded = joblib.load(MODEL_PATH)
loaded_meta = json.loads(META_PATH.read_text(encoding="utf-8"))
re_all, re_rate, re_ans = score(loaded, loaded_meta["threshold"])

assert np.isclose(re_all, acc_all) and np.isclose(re_ans, acc_ans), "불러온 모델의 점수가 다름"
print(f"검증 통과: 정확도 {re_all:.3f}, 기준 {loaded_meta['threshold']} → 답변율 {re_rate:.2f}, 정확도 {re_ans:.3f}")