# DECISION_MISMATCH — 판단 불일치

> Nuada Airgetlám 점검 매뉴얼 (연구용 프로토타입, 의료기기 매뉴얼 아님). 로봇팔 재개는 관제 화면의 수동 재개로만 한다.
>
> 참고 자료 (이 프로젝트의 설계 결정)
> - backend-spring/src/main/resources/application.yaml: gateway.move-threshold(0.75)
> - backend-spring/.../gateway/DecisionGate.java: 관문이 확신도 기준을 다시 계산해 판별기의 판단과 비교
> - ai-server-python/models/model_s3.json: 판별기 기준값 0.75 (관문 기준과 같게 맞춤)


## 증상
- 판별기가 보낸 판단(MOVE/ABSTAIN)과 관문이 같은 확신도로 다시 계산한 판단이 다르다.
- 관문 판단 기록에 DECISION_MISMATCH 가 나타나고 로봇팔이 정지된다.

## 원인
- 판별기와 관문의 확신도 기준값(threshold) 설정이 서로 다름.
- 판별기 모델 파일이나 설정이 바뀌었는데 관문 설정은 그대로임.
- 판별기 프로그램 오류(버그) 또는 메시지 변조.

## 확인 방법
1. 판단 기록의 상세(python=… gateway=… th=…)를 확인한다.
2. 판별기 모델 정보 파일(model_s3.json)의 threshold 와 관문 설정(gateway.move-threshold)을 비교한다.
3. 최근 판별기 코드·모델 변경 이력을 확인한다.

## 해결 단계
1. 설정이 다르면 연구책임자 확인 후 한쪽 기준에 맞춘다 (보통 학습 때 정한 기준).
2. 판별기를 다시 시작한다.
3. 판단이 일치하는 연속 정상 데이터가 쌓이면 관제 담당자가 수동 재개를 요청한다.

## 안 될 때
- 원인이 판별기 오류로 의심되면 세션을 종료하고, 판별기를 고칠 때까지 로봇팔을 사용하지 않는다.
