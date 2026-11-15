### 판별기 개발 환경 구성 / 2026-11-15
- 문제: EEG 판별기 실험을 위한 Python 환경이 없었음 (학원 PC, conda base 환경만 존재)
- AI 도움: Claude — conda 환경 생성·라이브러리 설치 명령, 환경 확인 예제 코드(step1_check_env.py) 제공.
  명령어 중복 붙여넣기로 난 ArgumentError의 원인 설명
- 내 판단과 행동:
    - 환경 이름 nuada-bci, Python 3.11 사용 (AI 제안을 채택)
    - 작업 PC는 학원 PC 위주, 실행 방식은 단계별 .py 파일로 결정
    - main 최신화 후 feature/eeg-classifier 브랜치 생성
    - 예제를 실행하고 직접 수정함: 예제를 실행하고 직접 수정함: 피험자 수를 출력하는 줄을 추가
      (print("피험자 수 :", len(dataset.subject_list))) → 출력 9
- 검증:
    - 조건: nuada-bci 환경에서 step1_check_env.py 실행
    - 예상 결과: [실행 전에 내가 예측한 피험자 수·상상 종류 수를 적기]
    - 실제 결과: Python 3.11.18, mne 1.13.2, moabb 1.7.2, scikit-learn 1.9.1, numpy 2.4.6 /
      BNCI2014-001, 피험자 9명, 상상 4종류(left_hand, right_hand, feet, tongue)
- 증거: step1_check_env.py, 실행 결과 화면, 브랜치 feature/eeg-classifier
- 한계와 다음 확인: 데이터는 아직 내려받지 않음. 학원 네트워크에서 다운로드가 되는지 확인 필요
- 상태: 검증

### IntelliJ에 conda Python 환경 연결 / 2026-11-15
- 문제: IntelliJ에서 .py 파일에 "구성된 Python 인터프리터가 없습니다" 경고와 빨간 밑줄 발생
- AI 도움: Claude — 원인 설명(IntelliJ가 nuada-bci 환경을 모름), 연결 절차 안내,
  모듈 SDK를 바로 바꾸면 Java 설정이 깨질 수 있다는 위험 설명, 패싯 방식 제안
- 내 판단과 행동:
    - 인터프리터 추가 화면의 기본값(새 환경 생성, Python 3.13)을 그대로 쓰지 않고 "기존 항목 선택"으로 변경
    - conda 목록 불러오기에서 오류 발생 (lateinit property envs_dirs has not been initialized)
      → 타입을 Python으로 바꿔 nuada-bci의 python.exe 경로를 직접 지정
    - base 환경(Python 3.14)이 선택된 것을 경로·버전 차이로 확인하고 envs\nuada-bci로 수정
- 검증:
    - 조건: IntelliJ에서 step1_check_env.py 실행
    - 예상 결과: nuada-bci의 Python 3.11로 실행되고 빨간 밑줄이 사라짐
    - 실제 결과: Python 3.11.18(Anaconda)로 실행, 피험자 수 9 출력, 경고 사라짐
- 증거: 실행 결과 화면, IntelliJ SDK 설정 화면
- 한계와 다음 확인: conda 목록 오류의 정확한 원인은 확인하지 않음 (conda 26 버전과의 호환 문제로 추정)
- 상태: 검증