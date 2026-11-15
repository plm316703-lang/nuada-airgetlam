### 판별기 개발 환경 구성 / 2026-11-15
- 문제: EEG 판별기 실험을 위한 Python 환경이 없었음 (학원 PC, conda base 환경만 존재)
- AI 도움: Claude — conda 환경 생성·라이브러리 설치 명령, 환경 확인 예제 코드(step1_check_env.py) 제공.
  명령어 중복 붙여넣기로 난 ArgumentError의 원인 설명
- 내 판단과 행동:
    - 환경 이름 nuada-bci, Python 3.11 사용 (AI 제안을 채택)
    - 작업 PC는 학원 PC 위주, 실행 방식은 단계별 .py 파일로 결정
    - main 최신화 후 feature/eeg-classifier 브랜치 생성
    - 예제를 실행하고 직접 수정함: [추가한 줄과 결과를 적기]
- 검증:
    - 조건: nuada-bci 환경에서 step1_check_env.py 실행
    - 예상 결과: [실행 전에 내가 예측한 피험자 수·상상 종류 수를 적기]
    - 실제 결과: Python 3.11.18, mne 1.13.2, moabb 1.7.2, scikit-learn 1.9.1, numpy 2.4.6 /
      BNCI2014-001, 피험자 9명, 상상 4종류(left_hand, right_hand, feet, tongue)
- 증거: step1_check_env.py, 실행 결과 화면, 브랜치 feature/eeg-classifier
- 한계와 다음 확인: 데이터는 아직 내려받지 않음. 학원 네트워크에서 다운로드가 되는지 확인 필요
- 상태: 검증