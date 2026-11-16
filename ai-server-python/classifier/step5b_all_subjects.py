# step5b_all_subjects.py
# 목적: step5와 똑같은 판별기를 9명 전원에게 적용해서,
#      "코드 문제인지, 5번 피험자가 어려운 것인지" 구분한다. (정상 동작 점검)

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)     # MOABB 내부 예고 경고 숨기기

import mne
mne.set_log_level("WARNING")                                   # CSP의 'Computing rank...' 로그 숨기기

import numpy as np
import pandas as pd
from moabb.datasets import BNCI2014_001
from moabb.paradigms import LeftRightImagery
from mne.decoding import CSP
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis as LDA
from sklearn.pipeline import make_pipeline

dataset = BNCI2014_001()
paradigm = LeftRightImagery()

rows = []                                                      # 피험자별 결과를 모을 목록
for subject in dataset.subject_list:                           # [1, 2, ..., 9]
    X, y, meta = paradigm.get_data(dataset=dataset, subjects=[subject])

    # step5와 똑같은 규칙으로 나누기: 정렬 첫 번째 = 학습, 두 번째 = 평가
    sessions = sorted(meta["session"].unique())
    train_mask = (meta["session"] == sessions[0]).to_numpy()
    test_mask  = (meta["session"] == sessions[1]).to_numpy()

    # 피험자마다 판별기를 새로 만든다 (이전 사람의 학습 결과가 섞이지 않게)
    clf = make_pipeline(CSP(n_components=6, log=True), LDA())
    clf.fit(X[train_mask], y[train_mask])
    acc = np.mean(clf.predict(X[test_mask]) == y[test_mask])

    rows.append({"subject": subject, "test_acc": round(acc, 3)})
    print(f"피험자 {subject}: {acc:.3f}")                       # 진행 상황 보기

# ── 표로 정리 ──────────────────────────────────────────
result = pd.DataFrame(rows)
print("\n", result.to_string(index=False))
print(f"\n9명 평균: {result['test_acc'].mean():.3f}")