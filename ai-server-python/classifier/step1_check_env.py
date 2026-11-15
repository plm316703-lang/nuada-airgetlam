# step1_check_env.py
# 목적: 설치한 라이브러리가 제대로 불러와지는지, 버전은 무엇인지 확인한다.
#      그리고 우리가 쓸 데이터셋(BCI Competition IV 2a)을 MOABB가 알고 있는지 확인한다.
#      ※ 이 파일은 데이터를 내려받지 않는다. (다운로드는 다음 단계)

import sys                      # 파이썬 자체 정보(버전 등)를 보는 기본 모듈

import mne                      # 뇌파 처리 라이브러리
import moabb                    # BCI 공개 데이터셋 라이브러리
import sklearn                  # scikit-learn (import 이름은 sklearn)
import numpy as np              # 숫자 계산 라이브러리 (관례상 np로 줄여 부름)

# ── 1) 버전 출력 ──────────────────────────────────────────
# 실험 기록에 남길 "어떤 환경에서 실험했는가"의 증거가 된다
print("Python      :", sys.version.split()[0])   # 예: 3.11.x
print("mne         :", mne.__version__)
print("moabb       :", moabb.__version__)
print("scikit-learn:", sklearn.__version__)
print("numpy       :", np.__version__)

# ── 2) 데이터셋 정보 확인 (다운로드 없이) ────────────────
# MOABB에서 BCI Competition IV 2a의 이름은 BNCI2014_001 이다
# (2014년에 BNCI Horizon 사이트에 올라간 1번째 데이터셋이라는 뜻)
from moabb.datasets import BNCI2014_001

dataset = BNCI2014_001()        # 데이터셋 "안내서" 객체를 만든다 (아직 데이터는 없음)

print("데이터셋 코드 :", dataset.code)           # 데이터셋 이름
print("피험자 목록   :", dataset.subject_list)   # 몇 번 피험자들이 있는지
print("상상 종류     :", dataset.event_id)       # 어떤 운동 상상(클래스)이 있는지
print("피험자 수     :", len(dataset.subject_list)) #목록의 길이 = 피험자 수