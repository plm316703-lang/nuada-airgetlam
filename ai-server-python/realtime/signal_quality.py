# signal_quality.py
# 목적: 시행(trial) 하나의 뇌파로 "신호 품질"을 계산하고, 시연용 오류를 주입한다.
#
# 공개 데이터(BCI IV 2a)에는 신호 품질 값이 없어서, 실제 신호에서 직접 계산한다.
#   - 평탄 채널 / 튀는 채널  → 전극 접촉 불량 (ELECTRODE_CONTACT)
#   - 60Hz 근처 힘이 큼      → 전원 잡음 (LINE_NOISE, 한국 전기는 60Hz)
#   - 40Hz 이상 힘이 큼      → 근육 잡음 (MUSCLE_ARTIFACT)
# 판별기 입력은 8~32Hz로 걸러진 신호라 정상일 때 60Hz·고주파 비율은 거의 0이다.
#
# 이 값은 연구용 근사치이며 의학적 판단이 아니다.

import numpy as np

SFREQ = 250.0

# BNCI2014_001 의 EEG 22채널 순서 (MOABB 데이터와 같은 순서)
CHANNELS = ["Fz", "FC3", "FC1", "FCz", "FC2", "FC4", "C5", "C3", "C1", "Cz", "C2",
            "C4", "C6", "CP3", "CP1", "CPz", "CP2", "CP4", "P1", "Pz", "P2", "POz"]

FLAT_RATIO = 0.1     # 채널 표준편차가 중앙값의 10% 미만 → 평탄(전극 떨어짐)
SPIKE_RATIO = 5.0    # 채널 표준편차가 중앙값의 5배 초과 → 튐(전극 흔들림)
BAND_LIMIT = 0.3     # 60Hz·고주파 힘이 전체의 30% 초과 → 잡음
MOTOR_CHANNELS = {"C3", "Cz", "C4"}
CHANNEL_PENALTY = 0.1
MOTOR_PENALTY = 0.2


def _band_ratio(x, lo, hi):
    """채널별로 [lo, hi] Hz 대역의 힘이 전체 힘에서 차지하는 비율 (0~1)"""
    spec = np.abs(np.fft.rfft(x, axis=-1)) ** 2
    freqs = np.fft.rfftfreq(x.shape[-1], d=1.0 / SFREQ)
    band = (freqs >= lo) & (freqs <= hi)
    total = spec.sum(axis=-1) + 1e-30          # 0으로 나누기 방지
    return spec[:, band].sum(axis=-1) / total


def assess(trial):
    """trial: (채널 22, 시간) 배열 → {"quality", "issue", "bad_channels"}"""
    stds = trial.std(axis=-1)
    med = float(np.median(stds)) + 1e-30
    flat = stds < FLAT_RATIO * med
    spike = stds > SPIKE_RATIO * med
    bad = flat | spike
    bad_channels = [CHANNELS[i] for i in np.flatnonzero(bad)]

    line = float(np.median(_band_ratio(trial, 58, 62)))
    muscle = float(np.median(_band_ratio(trial, 40, 120)))

    # 나쁜 채널 하나당 0.1, 운동 영역(C3·Cz·C4) 채널이면 0.2 더 깎는다
    # (왼손/오른손 상상은 C3·C4 근처 신호로 판별하므로 이 채널이 망가지면 치명적)
    motor_bad = sum(1 for name in bad_channels if name in MOTOR_CHANNELS)
    channel_score = max(0.0, 1.0 - CHANNEL_PENALTY * len(bad_channels) - MOTOR_PENALTY * motor_bad)
    noise_score = 1.0 - min(1.0, max(line, muscle))
    quality = round(float(np.clip(channel_score * noise_score, 0.0, 1.0)), 3)

    issue = None
    if bad.any():
        issue = "ELECTRODE_CONTACT"
    elif line > BAND_LIMIT:
        issue = "LINE_NOISE"
    elif muscle > BAND_LIMIT:
        issue = "MUSCLE_ARTIFACT"
    return {"quality": quality, "issue": issue, "bad_channels": bad_channels}


# ─────────────────────────────────────────────
# 시연용 오류 주입 (실제 신호를 복사해서 망가뜨림 → 판별기도 그 신호로 판별)
# ─────────────────────────────────────────────

def inject(trial, kind, rng):
    x = trial.copy()
    scale = float(np.median(x.std(axis=-1)))
    t = np.arange(x.shape[-1]) / SFREQ
    if kind == "electrode":
        # C3·C4 전극이 떨어짐(평탄) + Cz 전극이 흔들림(큰 출렁임)
        for name in ("C3", "C4"):
            x[CHANNELS.index(name)] *= 0.001
        x[CHANNELS.index("Cz")] += 12 * scale * np.sin(2 * np.pi * 1.5 * t)
    elif kind == "noise":
        # 전원선 60Hz 잡음이 모든 채널에 섞임
        x += 4 * scale * np.sin(2 * np.pi * 60 * t + rng.uniform(0, 2 * np.pi))
    elif kind == "muscle":
        # 턱·목 근육 긴장: 40Hz 이상 넓은 대역 잡음
        noise = rng.standard_normal(x.shape)
        spec = np.fft.rfft(noise, axis=-1)
        freqs = np.fft.rfftfreq(x.shape[-1], d=1.0 / SFREQ)
        spec[:, freqs < 40] = 0
        hf = np.fft.irfft(spec, n=x.shape[-1], axis=-1)
        x += 3 * scale * hf / (hf.std() + 1e-30)
    else:
        raise ValueError(f"모르는 신호 오류 종류: {kind}")
    return x
