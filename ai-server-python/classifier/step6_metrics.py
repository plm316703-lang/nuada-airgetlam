# step6_metrics.py
# 목적: 정확도 외에 '혼동 행렬'과 '카파'로 판별기를 평가한다.
#      어려운 피험자(5)와 잘 되는 피험자(3)를 나란히 비교한다.
#      ※ 학습/평가 규칙은 step5와 동일 (0train으로 학습, 1test로 평가)

import warnings
warnings.filterwarnings("ignore", category = FutureWarning)# MOABB 내부 예고 경고 숨기기

import mne
mne.set_log_level("WARNING")  # CSP 로그 숨기기


import numpy as np
import pandas as pd
from moabb.datasets import BNCI2014_001
from moabb.paradigms import LeftRightImagery
from mne.decoding import CSP
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis as LDA
from sklearn.pipeline import make_pipeline
from sklearn.metrics import confusion_matrix, cohen_kappa_score   # 새로 쓰는 두 도구


SUBJECTS = [5, 3]   # 비교용: 어려운 사람, 잘 되는 사람
LABELS = ["left_hand", "right_hand"]    # 표의 순서를 고정 (행·열이 뒤바뀌지 않게)

dataset = BNCI2014_001()
paradigm = LeftRightImagery()

for subject in SUBJECTS:
    # 1) 데이터 + 세션 분리
    X, y, meta = paradigm.get_data(dataset=dataset, subjects=[subject])
    sessions = sorted(meta["session"].unique())
    train_mask = (meta["session"] == sessions[0]).to_numpy()
    test_mask  = (meta["session"] == sessions[1]).to_numpy()
    # ── 2) 학습 → 예측 ─────────────────────────────────
    clf = make_pipeline(CSP(n_components=6, log=True), LDA())
    clf.fit(X[train_mask], y[train_mask])
    y_true = y[test_mask]   #실제 정답
    y_pred = clf.predict(X[test_mask])        #판별기의 답


    # ── 3) 세 가지 성적표 ──────────────────────────────
    acc   = np.mean(y_pred == y_true)
    kappa = cohen_kappa_score(y_true, y_pred)                  # 0 = 찍기 수준, 1 = 완벽

    # 혼동 행렬: 행 = 실제 정답, 열 = 판별기의 답
    cm = confusion_matrix(y_true, y_pred, labels=LABELS)
    cm_table = pd.DataFrame(
        cm,
        index=["실제 왼손", "실제 오른손"],
        columns=["답: 왼손", "답: 오른손"],
    )

    # 판별기가 '왼손'이라고 답한 비율 → 0.5에서 멀면 한쪽으로 쏠린 것
    left_ratio = np.mean(y_pred == "left_hand")

    print(f"\n===== 피험자 {subject} =====")
    print(f"정확도 : {acc:.3f}")
    print(f"카파   : {kappa:.3f}")
    print(f"'왼손'이라고 답한 비율: {left_ratio:.2f}  (72/144 = 0.50이면 쏠림 없음)")
    print(cm_table)
