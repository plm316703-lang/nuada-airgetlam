# step3_load_data.py
# 목적: step2에서 뽑힌 피험자(5번)의 데이터만 내려받고, 데이터의 "모양"을 확인한다.
#      ※ 아직 자르거나 학습하지 않는다. 무엇이 들어 있는지 눈으로 보는 단계.
#      ※ 첫 실행 때 인터넷에서 파일을 받는다 (피험자 1명분, 수십 MB 정도).
from moabb.datasets  import BNCI2014_001

SUBJECT = 5
dataset = BNCI2014_001()
# ── 1) 데이터 가져오기 ──────────────────────────────────
# 처음이면 내려받고, 이미 받았으면 PC에 있는 파일을 읽는다.
# 결과 모양: { 피험자번호: { 세션이름: { 런이름: Raw객체 } } }
data = dataset.get_data(subjects=[SUBJECT])
sessions = data[SUBJECT]        #이 피험자의 세션들
print("세션 이름들 : ", list(sessions.keys()))

# ── 2) 세션마다 런(run)이 몇 개인지 ─────────────────────
# 세션 = 하루 동안의 기록, 런 = 그 안에서 쉬는 시간 사이의 한 덩어리
for session_name, runs in sessions.items():
    print(f"[{session_name}] 런 개수 : {len(runs)}, 런 이름들 : {list(runs.keys())}")

# ── 3) 첫 세션의 첫 런 하나만 자세히 보기 ───────────────
first_session = list(sessions.keys())[0]
first_run = list(sessions[first_session].keys())[0]
raw = sessions[first_session][first_run]      # Raw = 자르기 전의 연속된 뇌파 기록


print("\n── 첫 런 자세히 ──")
print("채널 수       :", len(raw.ch_names))
print("채널 이름     :", raw.ch_names)
print("측정 속도(Hz) :", raw.info["sfreq"])                    # 1초에 몇 번 측정했나
print("기록 길이(초) :", round(raw.n_times / raw.info["sfreq"], 1))
print("채널 종류별 개수:", raw.get_channel_types(unique=False).count("eeg"), "개 EEG")
