# step2_pick_subject.py
# 목적: BCI IV 2a의 피험자 9명 중 1명을 "무작위로, 하지만 재현 가능하게" 뽑는다.
#      ※ 규칙: 시드를 먼저 정하고 기록한 뒤에 실행한다. (결과를 보고 고르지 않았다는 증거)
#      ※ 이 파일은 데이터를 내려받지 않는다. 피험자 번호만 정한다.

import random       # 파이썬 기본 무작위 모듈
from moabb.datasets import BNCI2014_001   #BCI IV 2a 데이터셋 안내서

# 1) 시드 portfolio-log.md에 미리 적어 둔 숫자를 넣는다.

SEED = 20261115

## (Spring 서비스에서 잘못된 요청을 예외로 막던 것과 같은 생각)
if SEED is None:
    raise ValueError("SEED가 비어 있습니다. 먼저 portfolio-log.md에 시드를 기록하고 넣으세요.")

subjects = BNCI2014_001().subject_list

rng = random.Random(SEED)       #이 시드로만 움직이는 "전용 주사위"
picked = rng.choice(subjects)       #목록에서 1명 고르기

print("시드         :", SEED)
print("후보 피험자  :", subjects)
print("뽑힌 피험자  :", picked)