# step4_epochs.py
# 목적: 연속 기록을 시행(trial) 단위로 자르고, 왼손·오른손만 골라서,
#      세션(학습일/평가일)별로 클래스 개수가 몇 개인지 확인한다.
#      ※ 아직 학습하지 않는다. "학습에 넣을 재료"가 제대로 준비됐는지 보는 단계.

import pandas as pd
from moabb.datasets import BNCI2014_001
from moabb.paradigms import LeftRightImagery # 왼손 vs 오른손 운동 상상 패러다임

SUBJECT = 5

dataset = BNCI2014_001()
paradigm = LeftRightImagery()

# ── 1) 어떤 기본값을 쓰는지 기록용으로 출력 ─────────────
# MOABB 버전에 따라 필터 대역이 저장된 이름이 다르다.
#   - 새 버전: paradigm.filters = [[fmin, fmax]]  (목록 안에 목록)
#   - 옛 버전: paradigm.fmin, paradigm.fmax
# getattr(객체, "이름", 기본값) = 그 이름이 있으면 값을, 없으면 기본값을 돌려준다
#   → 버전이 달라도 오류 없이 동작하게 만드는 안전장치
filters = getattr(paradigm, "filters", None)
if filters is None:                                    # 옛 버전이면
    filters = [[paradigm.fmin, paradigm.fmax]]

print("주파수 필터(Hz)  :", filters)                   # 예: [[8, 32]]
print("자르는 구간(초)  :", dataset.interval)          # 예: [2, 6]
print("tmin / tmax(초)  :", paradigm.tmin, "/", paradigm.tmax)  # None이면 dataset.interval 그대로 사용

import moabb
print("MOABB 버전       :", moabb.__version__)          # 포트폴리오 기록용

# ── 2) 자르기 + 고르기 + 거르기를 한 번에 ───────────────
# X    : 뇌파 숫자 덩어리   모양 = (시행 수, 채널 수, 시간 점 수)
# y    : 각 시행의 정답      예: 'left_hand', 'right_hand'
# meta : 각 시행의 정보표    어느 세션·어느 런에서 나왔는지
X, y, meta = paradigm.get_data(dataset=dataset, subjects=[SUBJECT])

print("\nX 모양 (시행, 채널, 시간점):", X.shape)
print("y 앞 5개           :", y[:5])

# ── 3) 세션별 × 클래스별 개수 세기 ─────────────────────
# crosstab = 두 기준(세션, 정답)으로 개수를 세서 표로 만들어 주는 함수
print("\n세션별 클래스 개수:")
print(pd.crosstab(meta["session"], y))
