# step5_train_csp_lda.py
# 목적: 학습 세션(0train)으로 CSP+LDA를 학습하고, 평가 세션(1test)으로 정확도를 잰다.
#      + 학습 세션 안에서 교차검증 점수도 함께 재서, 두 점수를 비교해 해석한다.

import warnings
warnings.filterwarnings("ignore", category=FutureWarning) #MOABB 내부 예고 경고 숨기기 (결과 영향 없음)

import numpy as np
from moabb.datasets import BNCI2014_001
from moabb.paradigms import LeftRightImagery
from mne.decoding import CSP                                   # 공간 필터 (특징 추출)
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis as LDA  # 선형 분류기
from sklearn.pipeline import make_pipeline                     # 여러 단계를 하나로 묶기
from sklearn.model_selection import StratifiedKFold, cross_val_score


SUBJECT = 5
SEED = 20261115

# - 1 데이터 가져오기
dataset = BNCI2014_001()
paradigm = LeftRightImagery()
X, y, meta = paradigm.get_data(dataset=dataset, subjects=[SUBJECT])

# ── 2) 세션 기준으로 학습/평가 나누기 ───────────────────
sessions = sorted(meta["session"].unique())  # ['0train', '1test']
assert len(sessions) == 2, f"세션이 2개가 아님: {sessions}"
train_sess, test_sess = sessions[0], sessions[1]

train_mask = (meta["session"] == train_sess).to_numpy()
test_mask = (meta["session"] == test_sess).to_numpy()
assert not (train_mask & test_mask).any(), "학습/평가 시행이 겹침" #겹치면 점수가 부풀려짐

X_train, y_train = X[train_mask], y[train_mask]
X_test, y_test = X[test_mask], y[test_mask]
print(f"학습: {train_sess} {X_train.shape} / 평가: {test_sess} {X_test.shape}")

# ── 3) 판별기: CSP(특징 6개) → LDA(선 긋기) ─────────────
clf = make_pipeline(
    CSP(n_components= 6, log = True),
    LDA(),
)
# ── 4) 학습 세션 안에서만 교차검증 (평가 세션은 건드리지 않음) ──
cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
cv_scores = cross_val_score(clf, X_train, y_train, cv=cv)
print(f"\n[A] 학습 세션 교차검증: {cv_scores.mean():.3f} ± {cv_scores.std():.3f}")

#── 5) 학습 세션 전체로 학습 → 평가 세션으로 시험 ────────
clf.fit(X_train, y_train)
acc = np.mean(clf.predict(X_test) == y_test)
print(f"[B] 평가 세션 정확도 : {acc:.3f}")
print("    (찍기 = 0.500, 시행 144개 기준으로 0.58 이상이면 '찍기보다 낫다')")


