# 목적: 판단 보류 기준값을 "평가 세션을 보지 않고" 학습 세션 안에서만 고른 뒤,
#      평가 세션에 딱 한 번 적용한다.
#      ※ 고르는 규칙은 실행 전에 portfolio-log.md에 기록해 두었다.

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

import mne
mne.set_log_level("WARNING")

import numpy as np
import pandas as pd
from moabb.datasets import BNCI2014_001
from moabb.paradigms import LeftRightImagery
from mne.decoding import CSP
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis as LDA
from sklearn.pipeline import make_pipeline
from sklearn.model_selection import StratifiedKFold, cross_val_predict

SUBJECTS = [5, 3]
SEED = 20261115
CANDIDATES = np.round(np.arange(0.50, 0.96, 0.05), 2)     # 후보 기준값: 0.50, 0.55, ..., 0.95
MIN_ANSWER_RATE = 0.90

def evaluate(confidence, correct, t):
    """기준값 T를 적용했을때 (답변율, 답한 것 중 정확도)를 돌려준다."""
    answered = confidence >= t  #기준 이상만 답함
    rate = answered.mean()
    acc = correct[answered].mean() if answered.any() else np.nan
    return rate, acc

dataset = BNCI2014_001()
paradigm = LeftRightImagery()

for subject in SUBJECTS:
    X, y, meta = paradigm.get_data(dataset=dataset, subjects=[subject])
    sessions = sorted(meta["session"].unique())
    train_mask = (meta["session"] == sessions[0]).to_numpy()
    test_mask  = (meta["session"] == sessions[1]).to_numpy()
    X_train, y_train = X[train_mask], y[train_mask]
    X_test,  y_test  = X[test_mask],  y[test_mask]

    clf = make_pipeline(CSP(n_components=6, log=True), LDA())
    # ── ② 학습 세션 안에서만: 교차검증으로 "처음 보는 시행"의 확률 구하기 ──
    # cross_val_predict = 5조각 중 4조각으로 학습 → 남은 1조각의 확률 예측을 5번 반복해서
    #                     144개 시행 모두에 대해 "학습에 안 쓰인 상태의" 확률을 모아 준다
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
    proba_cv = cross_val_predict(clf, X_train, y_train, cv= cv, method="predict_proba")

    classes = np.unique(y_train)                           # 확률 열 순서 = 정렬된 클래스 이름
    conf_cv = proba_cv.max(axis=1)
    correct_cv = classes[proba_cv.argmax(axis=1)] == y_train
    # ── ③ 규칙으로 기준값 하나 고르기 ─────────────────────
    rows = []
    for t in CANDIDATES:
        rate, acc = evaluate(conf_cv, correct_cv, t)
        rows.append({"기준값": t, "답변율": round(rate, 2), "정확도": round(acc, 3)})
    table = pd.DataFrame(rows)

    ok = table[table["답변율"] >= MIN_ANSWER_RATE]          # 규칙 1: 답변율 90% 이상만
    # 규칙 2: 그중 정확도가 가장 높은 값, 같으면 더 낮은 기준값 (표가 이미 오름차순이라 첫 번째가 낮은 값)
    chosen = ok.loc[ok["정확도"].idxmax(), "기준값"] if len(ok) else 0.5

    # ── ④ 학습 세션 전체로 다시 학습 → 평가 세션에 한 번만 적용 ──
    clf.fit(X_train, y_train)
    proba_test = clf.predict_proba(X_test)
    conf_test = proba_test.max(axis=1)
    correct_test = clf.classes_[proba_test.argmax(axis=1)] == y_test

    base_rate, base_acc = evaluate(conf_test, correct_test, 0.5)      # 보류 없음 (비교용)
    rate, acc = evaluate(conf_test, correct_test, chosen)             # 고른 기준값

    print(f"\n===== 피험자 {subject} =====")
    print("[학습 세션 교차검증] 기준값별 표")
    print(table.to_string(index=False))
    print(f"→ 규칙으로 고른 기준값: {chosen}")
    print(f"[평가 세션] 보류 없음 : 답변율 {base_rate:.2f}, 정확도 {base_acc:.3f}")
    print(f"[평가 세션] 기준 {chosen}: 답변율 {rate:.2f}, 정확도 {acc:.3f}")

