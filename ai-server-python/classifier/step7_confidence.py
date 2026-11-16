# step7_confidence.py
# 목적: 판별기의 확신도(확률)를 꺼내서,
#      (1) 맞힌 시행과 틀린 시행의 확신도가 다른지
#      (2) 확신 낮은 시행을 버리면 정확도와 답변율이 어떻게 바뀌는지 관찰한다.
#      ※ 관찰만 한다. 기준값은 여기서 고르지 않는다 (평가 세션으로 고르면 안 됨 → step8에서 학습 세션으로)


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

SUBJECTS = [5, 3]       #어려운 사람, 잘되는 사람
THRESHOLDS = [0.5, 0.6, 0.7, 0.8, 0.9]      #관찰용 기준값들 (0.5 = 보류 없음)

dataset = BNCI2014_001()
paradigm = LeftRightImagery()

for subject in SUBJECTS:
    # ── 1) 데이터 + 세션 분리 + 학습 (step5와 동일) ───────
    X, y, meta = paradigm.get_data(dataset=dataset, subjects=[subject])
    sessions = sorted(meta["session"].unique())
    train_mask = (meta["session"] == sessions[0]).to_numpy()
    test_mask  = (meta["session"] == sessions[1]).to_numpy()

    clf = make_pipeline(CSP(n_components=6, log=True), LDA())
    clf.fit(X[train_mask], y[train_mask])

    # ── 2) 확률 꺼내기 ─────────────────────────────────
    # predict_proba → 모양 (시행 수, 2). 각 줄 = [클래스A 확률, 클래스B 확률], 합은 1
    # 열 순서는 clf.classes_ 에 적혀 있다 (보통 알파벳순: left_hand, right_hand)
    proba = clf.predict_proba(X[test_mask])
    y_true = y[test_mask]
    y_pred = clf.classes_[proba.argmax(axis=1)]  # 더 큰 확률 쪽 = 판별기의 답 (predict와 같음)

    confidence = proba.max(axis = 1)        #확신도 = 둥 중 큰확률 (0.5 ~1.0)
    correct = (y_pred == y_true)  #맞혔으면 True

    confidence = proba.max(axis=1)                 # 확신도 = 둘 중 큰 확률 (0.5 ~ 1.0)
    correct = (y_pred == y_true)                   # 맞혔으면 True

    print(f"\n===== 피험자 {subject} =====")
    print("클래스 순서:", clf.classes_)
    print("첫 3개 시행 확률:\n", np.round(proba[:3], 3))

    # ── 3) 맞힌 시행 vs 틀린 시행의 평균 확신도 ─────────
    print(f"맞힌 시행 평균 확신도: {confidence[correct].mean():.3f}  ({correct.sum()}개)")
    print(f"틀린 시행 평균 확신도: {confidence[~correct].mean():.3f}  ({(~correct).sum()}개)")

    # ── 4) 기준값별: 답변율과 답한 것 중 정확도 ─────────
    rows = []
    for t in THRESHOLDS:
        answered = confidence >= t                 # 기준 이상만 "답함", 나머지는 보류
        rows.append({
            "기준값": t,
            "답변율": round(answered.mean(), 2),    # 전체 중 답한 비율
            "답한 것 중 정확도": round(correct[answered].mean(), 3) if answered.any() else None,
            "보류 수": int((~answered).sum()),
        })
    print(pd.DataFrame(rows).to_string(index=False))