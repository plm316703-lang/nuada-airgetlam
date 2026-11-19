# SIGNAL_QUALITY_LOW — 신호 품질 저하 (원인 불명)

> Nuada Airgetlám 점검 매뉴얼 (연구용 프로토타입, 의료기기 매뉴얼 아님). 로봇팔 재개는 관제 화면의 수동 재개로만 한다.
>
> 참고 자료
> - FieldTrip, How can I inspect the electrode impedances of my data? (임피던스가 높을수록 신호 대 잡음비가 낮아짐). https://www.fieldtriptoolbox.org/faq/preproc/dataformat/impedancecheck/
> - OpenBCI GUI 위젯 안내 (Railed 경고 = 보통 전극 접촉 불량). https://docs.openbci.com/Software/OpenBCISoftware/GUIWidgets/
> - 이 매뉴얼의 원인별 점검은 ELECTRODE_CONTACT, LINE_NOISE, MUSCLE_ARTIFACT 매뉴얼의 출처를 따른다.

## 증상
- 신호 품질이 기준(0.6) 아래지만 판별기가 특정 원인(전극·전원·근육)을 지목하지 못한다.

## 원인
- 여러 원인이 약하게 겹침 (약간의 접촉 불량 + 약간의 움직임 등).
- 장비 증폭기 이상, 데이터 전송 중 일부 손상.

## 확인 방법
1. 관제 화면의 최근 신호 품질 그래프에서 언제부터 떨어졌는지 확인한다.
2. 전극 접촉 불량, 전원 잡음, 근육 잡음 매뉴얼의 확인 방법을 차례로 수행한다.
3. 장비 상태 표시등과 배터리 잔량을 확인한다.

## 해결 단계
1. 로봇팔은 이미 정지 상태다.
2. 위 확인 결과에 맞는 매뉴얼의 해결 단계를 따른다.
3. 신호 품질이 회복되고 연속 정상 데이터가 쌓이면 관제 담당자가 수동 재개를 요청한다.

## 안 될 때
- 장비를 다시 시작하고 임피던스 측정부터 다시 한다.
- 원인을 찾지 못하면 세션을 종료하고 오류 기록을 연구책임자에게 전달한다.
