# replayer.py — 판별기 재생기 (메시지 규격 버전 2)
#
# 공개 뇌파 데이터(BCI IV 2a, 피험자 3의 평가 세션)를 시행 하나씩 "실시간처럼" 재생하며,
# 저장된 CSP+LDA 모델로 왼손/오른손을 판별하고, 신호 품질을 계산해 Spring 관문으로 보낸다.
#
# 시연용 오류 주입 (화면·기록에 "주입" 표시됨, 성능 지표에서 제외):
#   electrode  전극 접촉 불량 (C3·C4 평탄 + Cz 출렁임)
#   noise      전원 잡음 60Hz
#   muscle     근육 잡음 (40Hz 이상)
#   mismatch   판별기 판단을 일부러 뒤집음 → 관문 판단 불일치
#   stale      5초 전 시각으로 보냄 → 오래된 명령
#   disconnect 5초 동안 보내지 않음 → 워치독 정지 (PACKET_LOSS)
#   reset      12초 동안 보내지 않음 → 초기화 필요
#
# 사용 예 (ai-server-python 폴더에서):
#   python realtime/replayer.py                                 # 정상 재생 (계속 반복)
#   python realtime/replayer.py --scenario electrode --at 8     # 8번째 시행부터 4개 전극 불량
#   실행 중 키 입력 (터미널):  e n m x s d r + Enter → 다음 시행부터 해당 오류 주입, q = 종료

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

import argparse
import json
import os
import sys
import threading
import time
from datetime import datetime, timedelta
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent          # ai-server-python
sys.path.insert(0, str(Path(__file__).resolve().parent))
import signal_quality as sq                              # noqa: E402  (같은 폴더 모듈)

SIGNAL_KINDS = {"electrode", "noise", "muscle"}
GAP_SECONDS = {"disconnect": 5.0, "reset": 12.0}
ALL_SCENARIOS = sorted(SIGNAL_KINDS | set(GAP_SECONDS) | {"mismatch", "stale"})
KEYS = {"e": "electrode", "n": "noise", "m": "muscle", "x": "mismatch", "s": "stale",
        "d": "disconnect", "r": "reset"}


def load_env():
    """저장소 최상위 .env 를 읽는다 (이미 있는 환경 변수는 덮어쓰지 않음 → Docker 환경 변수 우선)"""
    try:
        from dotenv import load_dotenv
    except ImportError:
        return
    load_dotenv(ROOT.parent / ".env", override=False)


def parse_args():
    p = argparse.ArgumentParser(description="Nuada 판별기 재생기 (규격 v2)")
    p.add_argument("--scenario", choices=ALL_SCENARIOS, default=os.environ.get("REPLAY_SCENARIO") or None,
                   help="정해진 시점에 주입할 오류 (기본: 없음)")
    p.add_argument("--at", type=int, default=int(os.environ.get("REPLAY_INJECT_AT", "8")),
                   help="몇 번째 시행부터 주입할지 (0부터)")
    p.add_argument("--length", type=int, default=4, help="신호 오류를 몇 시행 동안 주입할지")
    p.add_argument("--trials", type=int, default=int(os.environ.get("REPLAY_TRIALS", "0")),
                   help="보낼 시행 수 (0 = 끝없이 반복)")
    p.add_argument("--interval", type=float, default=1.0, help="시행 간격(초)")
    return p.parse_args()


def load_model_and_data():
    import joblib
    import mne
    from moabb.datasets import BNCI2014_001
    from moabb.paradigms import LeftRightImagery
    mne.set_log_level("WARNING")

    model_dir = ROOT / "models"
    model = joblib.load(model_dir / "model_s3.joblib")
    meta = json.loads((model_dir / "model_s3.json").read_text(encoding="utf-8"))

    print("데이터 불러오는 중… (처음이면 약 80MB 다운로드)", flush=True)
    X, y, info = LeftRightImagery().get_data(dataset=BNCI2014_001(), subjects=[meta["subject"]])
    test = (info["session"] == "1test").to_numpy()        # 학습에 쓰지 않은 다른 날 세션만 재생
    X_test, y_test = X[test], y[test]
    pre = meta["preprocess"]
    assert X_test.shape[1] == pre["n_channels"], "채널 수가 학습 때와 다름"
    assert X_test.shape[2] == pre["n_times"], "시간 점 수가 학습 때와 다름"
    return model, meta, X_test, y_test


class Injector:
    """지금 주입 중인 오류 상태. 키보드 스레드와 전송 스레드가 함께 쓰므로 잠금 사용"""

    def __init__(self, length):
        self.lock = threading.Lock()
        self.kind = None
        self.left = 0
        self.length = length

    def trigger(self, kind):
        with self.lock:
            self.kind = kind
            self.left = self.length if kind in SIGNAL_KINDS else 1
        print(f"\n▶ 다음 시행부터 '{kind}' 주입", flush=True)

    def take(self):
        """이번 시행에 적용할 오류 종류 (없으면 None)"""
        with self.lock:
            if self.left <= 0:
                return None
            self.left -= 1
            return self.kind


def keyboard_loop(injector, stop_event):
    print("키 입력: e=전극 n=전원잡음 m=근육 x=판단불일치 s=오래된명령 d=끊김(5초) r=끊김(12초) q=종료", flush=True)
    for line in sys.stdin:
        key = line.strip().lower()[:1]
        if key == "q":
            stop_event.set()
            return
        if key in KEYS:
            injector.trigger(KEYS[key])


def build_message(seq, trial, label, model, meta, kind, rng):
    """시행 하나 → 규격 v2 메시지 (dict)"""
    x = sq.inject(trial, kind, rng) if kind in SIGNAL_KINDS else trial
    signal = sq.assess(x)
    proba = model.predict_proba(x[np.newaxis])[0]
    best = int(proba.argmax())
    confidence = float(proba[best])
    threshold = meta["threshold"]
    decision = "MOVE" if confidence >= threshold else "ABSTAIN"
    if kind == "mismatch":
        decision = "ABSTAIN" if decision == "MOVE" else "MOVE"   # 판별기 쪽 이상을 흉내냄

    sent_at = datetime.now().astimezone()                         # 시간대(+09:00) 포함
    if kind == "stale":
        sent_at -= timedelta(seconds=5)

    return {
        "version": 2,
        "type": "classification",
        "seq": seq,
        "timestamp": sent_at.isoformat(timespec="milliseconds"),
        "subject": meta["subject"],
        "intent": str(model.classes_[best]),
        "confidence": round(confidence, 3),
        "threshold": threshold,
        "decision": decision,
        "signal": signal,
        "scenario": kind,                                        # 정상이면 None → JSON null
        "true_label": str(label),                                # 시연 전용 정답 (관문 판단에 쓰지 않음)
    }


def main():
    load_env()
    args = parse_args()
    url = os.environ.get("GATEWAY_URL", "ws://localhost:8080/ws/classifier")
    api_key = os.environ.get("GATEWAY_API_KEY")
    if not api_key:
        raise SystemExit("환경 변수 GATEWAY_API_KEY가 없습니다. 저장소 최상위 .env 를 확인하세요.")

    from websockets.sync.client import connect
    from websockets.exceptions import ConnectionClosed, InvalidStatus

    model, meta, X_test, y_test = load_model_and_data()
    rng = np.random.default_rng(20261117)
    injector = Injector(args.length)
    stop_event = threading.Event()
    if sys.stdin and sys.stdin.isatty():
        threading.Thread(target=keyboard_loop, args=(injector, stop_event), daemon=True).start()

    sent = 0
    trial_index = 0
    while not stop_event.is_set():
        try:
            with connect(url, additional_headers={"X-API-Key": api_key}, open_timeout=5) as ws:
                print(f"연결 성공: {url} · 피험자 {meta['subject']} · 기준값 {meta['threshold']}", flush=True)
                seq = 0                                         # 새 연결 = seq 처음부터 (관문 규격)
                while not stop_event.is_set():
                    if args.trials and sent >= args.trials:
                        print("\n지정한 시행 수를 모두 보냄 → 정상 종료", flush=True)
                        return
                    if args.scenario and sent == args.at:
                        injector.trigger(args.scenario)

                    kind = injector.take()
                    if kind in GAP_SECONDS:
                        print(f"  … {GAP_SECONDS[kind]:.0f}초 동안 보내지 않음 ({kind})", flush=True)
                        time.sleep(GAP_SECONDS[kind])
                        kind = None                             # 끊김 뒤 첫 메시지는 정상 데이터

                    i = trial_index % len(X_test)
                    msg = build_message(seq, X_test[i], y_test[i], model, meta, kind, rng)
                    ws.send(json.dumps(msg, ensure_ascii=False))
                    tag = f" [주입:{kind}]" if kind else ""
                    print(f"seq={seq:4d} {msg['intent']:<10} conf={msg['confidence']:.3f} "
                          f"q={msg['signal']['quality']:.2f} {msg['decision']:<7}{tag}", flush=True)
                    seq += 1
                    sent += 1
                    trial_index += 1
                    time.sleep(args.interval)
        except InvalidStatus as e:
            raise SystemExit(f"연결 거절: HTTP {e.response.status_code} (API 키 확인)")
        except (ConnectionRefusedError, ConnectionClosed, OSError, TimeoutError) as e:
            if stop_event.is_set():
                break
            print(f"연결 실패/끊김: {e!r} → 2초 뒤 다시 연결", flush=True)
            time.sleep(2)
    print("종료", flush=True)


if __name__ == "__main__":
    main()
