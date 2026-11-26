# capture_demo.py — README용 데모 GIF와 화면 사진을 자동으로 찍는다
#
#   Docker로 띄운 시연 환경(http://localhost:8081)에 Chrome(창 없이)으로 접속해
#   관제 화면: 정상 동작 → 오류 주입(전극 불량) → 정지 + 채널 지도 + RAG 안내 → 연속 정상 → 수동 재개
#   를 순서대로 찍어 docs/images/ 에 저장한다. 사람이 손으로 찍으면 매번 장면이 달라져서 스크립트로 남긴다.
#
# 준비: docker compose up (재생기가 세션 데이터를 보내는 중), 저장소 최상위 .env 의 DEMO_PASSWORD
# 필요한 것: Chrome 또는 Edge, Python 패키지 pillow · websocket-client (Anaconda 기본 환경에 있음)
# 실행 (저장소 최상위에서):  python scripts\capture_demo.py
#
# 주의: 시연용 DB에 오류 주입·정지·재개 기록이 실제로 남는다 (시연 환경에서만 실행할 것).

import base64
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

import websocket
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "images"
BASE = os.environ.get("DEMO_URL", "http://localhost:8081")
PORT = 9333
BROWSERS = [r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"]


def env_value(name):
    """저장소 최상위 .env 에서 값 하나만 읽는다 (화면·로그에는 출력하지 않음)"""
    if os.environ.get(name):
        return os.environ[name]
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip().strip('"')
    raise SystemExit(f".env 에 {name} 이 없습니다")


class Browser:
    """Chrome DevTools Protocol을 직접 부르는 아주 작은 도우미 (Selenium·Playwright 설치 없이)"""

    def __init__(self):
        exe = next((b for b in BROWSERS if Path(b).exists()), None)
        if not exe:
            raise SystemExit("Chrome 또는 Edge를 찾지 못했습니다")
        self.profile = tempfile.mkdtemp(prefix="nuada-capture-")   # 평소 쓰는 브라우저 프로필과 섞이지 않게
        # 창 없는 Chrome도 관제 화면(그래프 애니메이션)을 그리느라 CPU를 꽤 쓴다. 같은 PC의 Docker 재생기가 밀리면
        # 메시지가 2초 넘게 늦어져 관문이 "오래된 명령"으로 막아 버리므로(11/26 실제로 겪음), 가장 낮은 우선순위로 띄운다
        self.proc = subprocess.Popen([exe, "--headless=new", f"--remote-debugging-port={PORT}",
                                      f"--user-data-dir={self.profile}", "--no-first-run", "--hide-scrollbars",
                                      "about:blank"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                     creationflags=getattr(subprocess, "IDLE_PRIORITY_CLASS", 0))
        for _ in range(50):
            try:
                targets = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json"))
                page = next(t for t in targets if t["type"] == "page")
                break
            except Exception:
                time.sleep(0.2)
        else:
            raise SystemExit("브라우저 디버깅 포트에 연결하지 못했습니다")
        self.ws = websocket.create_connection(page["webSocketDebuggerUrl"], suppress_origin=True)
        self.n = 0
        self.call("Page.enable")

    def call(self, method, **params):
        self.n += 1
        self.ws.send(json.dumps({"id": self.n, "method": method, "params": params}))
        while True:                                  # 중간에 오는 이벤트 메시지는 건너뛰고 내 응답만 기다린다
            msg = json.loads(self.ws.recv())
            if msg.get("id") == self.n:
                if "error" in msg:
                    raise RuntimeError(f"{method}: {msg['error']}")
                return msg.get("result", {})

    def js(self, expr):
        r = self.call("Runtime.evaluate", expression=expr, awaitPromise=True, returnByValue=True)
        if "exceptionDetails" in r:
            raise RuntimeError(r["exceptionDetails"].get("text"))
        return r["result"].get("value")

    def size(self, w, h, mobile=False, scale=1):
        self.call("Emulation.setDeviceMetricsOverride", width=w, height=h, deviceScaleFactor=scale, mobile=mobile)

    def goto(self, path, settle=2.5):
        self.call("Page.navigate", url=BASE + path)
        for _ in range(100):
            if self.js("document.readyState") == "complete":
                break
            time.sleep(0.1)
        time.sleep(settle)                           # 화면이 첫 데이터를 받아 그릴 시간
        self.js("window.confirm = () => true")       # 확인 창이 뜨면 자동으로 '확인' (창 없이 실행 중이라 누를 수 없음)

    def shot(self, full=False):
        params = {"format": "jpeg", "quality": 92}       # PNG보다 브라우저가 빨리 만든다 (최종 파일은 PNG·GIF로 다시 저장)
        if full:                                     # 페이지 전체 높이로 늘려서 한 장에
            h = self.call("Page.getLayoutMetrics")["cssContentSize"]["height"]
            params["clip"] = {"x": 0, "y": 0, "width": self.js("innerWidth"), "height": h, "scale": 1}
            params["captureBeyondViewport"] = True
        data = self.call("Page.captureScreenshot", **params)["data"]
        return Image.open(io.BytesIO(base64.b64decode(data))).convert("RGB")

    def login(self, email, required=True):
        self.goto("/login.html", settle=0.5)
        status = self.js("fetch('/api/auth/login', {method: 'POST', headers: {'Content-Type': 'application/json'}, "
                         f"body: JSON.stringify({{email: {json.dumps(email)}, password: {json.dumps(env_value('DEMO_PASSWORD'))}}})}})"
                         ".then(r => r.status)")
        if status != 200 and required:
            raise SystemExit(f"{email} 로그인 실패 (HTTP {status})")
        return status == 200

    def close(self):
        self.ws.close()
        self.proc.terminate()
        self.proc.wait(timeout=10)
        shutil.rmtree(self.profile, ignore_errors=True)


def arm_state(b):
    return b.js("document.getElementById('armState').textContent.trim()")


def wait_until(b, expr, timeout, frames=None, every=1.0):
    """조건이 참이 될 때까지 기다리며, frames 를 주면 그동안의 장면도 찍어 둔다"""
    end = time.time() + timeout
    while time.time() < end:
        ok = b.js(expr)
        if frames is not None:               # 조건을 확인한 "뒤에" 찍어야 마지막 장면이 바뀐 상태를 보여 준다
            frames.append(b.shot())
        if ok:
            return True
        time.sleep(every)
    return False


# 재개 버튼은 '정지'이기만 하면 켜지고, 연속 정상 데이터 조건은 누른 뒤 서버가 검사한다.
# 그래서 버튼 대신 화면의 "N개 (재개 기준 M)" 글자로 조건이 채워졌는지 본다 (누르면 거부 기록만 남으므로)
RESUME_READY = ("(() => { const m = document.getElementById('cleanText').textContent.match(/(\\d+)개 \\(재개 기준 (\\d+)\\)/); "
                "return document.getElementById('armState').textContent.trim() === '정지' && !!m && +m[1] >= +m[2]; })()")
RUNNING = "document.getElementById('armState').textContent.trim() === '동작 중'"
# 가장 최근 오류 카드(맨 위)가 방금 주입한 전극 오류이고, RAG 안내를 다 받았는가
RAG_DONE = ("(() => { const c = document.querySelector('#errors .err'); "
            "return !!c && c.textContent.includes('전극') && !c.textContent.includes('받는 중'); })()")


# 최근 판단 5개가 모두 정상(MOVE/ABSTAIN)인가. 이 PC(4코어)에서는 메시지가 2초 넘게 밀려 "오래된 명령"으로 막히는 순간이 있어,
# 그 상태에서 주입하면 전극 오류 대신 지연 오류가 찍힌다 → 흐름이 안정된 뒤에만 주입한다
STEADY = ("(() => { const rows = [...document.querySelectorAll('#decisions tr')].slice(0, 5).map(r => r.textContent); "
          "return rows.length === 5 && rows.every(t => /MOVE|ABSTAIN/.test(t) && !/BLOCKED|HELD/.test(t)); })()")


def resume(b):
    b.js("document.getElementById('btnResume').click()")
    if not wait_until(b, RUNNING, 10):
        raise SystemExit("수동 재개가 받아들여지지 않았습니다 (관제 화면의 재개 거부 사유 확인)")


def text_of(b, element_id):
    return b.js(f"document.getElementById('{element_id}').textContent")


def record_dashboard(b):
    """관제 화면 시나리오를 찍어 (장면 목록, 길게 보여 줄 장면 번호, 오류 직후 전체 화면) 을 돌려준다"""
    b.login("operator@nuada.test")
    b.size(1400, 1450)                               # RAG 안내 카드까지 한 화면에 들어오는 높이
    b.goto("/dashboard.html", settle=4)
    if not b.js("!!document.querySelector('#sessionBox button.danger')"):
        raise SystemExit("진행 중인 측정 세션이 없습니다. 관제 화면에서 세션을 시작한 뒤 다시 실행하세요.")

    # 시작 전 정지 상태라면 먼저 재개해서 "정상 동작" 장면부터 시작한다
    if arm_state(b) != "동작 중":
        if not wait_until(b, RESUME_READY, 30):
            raise SystemExit(f"팔이 '{arm_state(b)}' 상태이고 연속 정상 데이터가 쌓이지 않습니다 (재생기 확인)")
        resume(b)
    if not wait_until(b, f"({RUNNING}) && {STEADY}", 60):
        raise SystemExit("팔이 동작 중이 아니거나 판단이 안정되지 않습니다 (메시지 지연·차단이 계속됨)")

    frames, hold = [], {}
    for _ in range(6):                               # 1) 정상 동작
        frames.append(b.shot()); time.sleep(0.6)
    b.js("[...document.querySelectorAll('#injectBox button')].find(x => x.textContent.includes('전극')).click()")
    if not wait_until(b, f"!({RUNNING})", 20, frames):
        raise SystemExit("오류를 주입했지만 팔이 멈추지 않았습니다")
    if "ELECTRODE" not in text_of(b, "stopReason"):
        raise SystemExit(f"전극 오류가 아닌 다른 이유로 멈췄습니다: {text_of(b, 'stopReason')} → 잠시 뒤 다시 실행")
    hold[len(frames) - 1] = 1500                     # 2) 정지된 순간
    if not wait_until(b, RAG_DONE, 40, frames):
        raise SystemExit("RAG 안내가 40초 안에 오지 않았습니다 (RAG 컨테이너 로그 확인)")
    if "생성 실패" in b.js("document.querySelector('#errors .err').textContent"):
        raise SystemExit("RAG 안내문 생성이 실패했습니다 (Gemini 과부하일 수 있음) → 잠시 뒤 다시 실행")
    hold[len(frames) - 1] = 3500                     # 3) RAG 안내가 도착한 순간 (읽을 시간)
    full = b.shot(full=True)
    wait_until(b, RESUME_READY, 40, frames, every=1.0)
    hold[len(frames) - 1] = 1500                     # 4) 연속 정상 데이터가 쌓여 재개 조건 충족
    resume(b)
    for _ in range(5):                               # 5) 수동 재개 후 다시 동작
        time.sleep(0.6); frames.append(b.shot())
    hold[len(frames) - 1] = 2500
    return frames, hold, full


def save_gif(frames, hold, path, width=960):
    small = [f.resize((width, round(f.height * width / f.width)), Image.LANCZOS) for f in frames]
    # 장면마다 자기 팔레트(256색)를 쓴다. 공통 팔레트 하나로 줄이면 면적이 작은 색(채널 지도의 빨간 점, 그래프 선)이
    # 회색으로 뭉개져 "문제 채널"이 안 보였다 (11/26 첫 결과물에서 확인). FASTOCTREE는 작은 색 덩어리도 남긴다
    pal = [f.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE) for f in small]
    durations = [hold.get(i, 450) for i in range(len(pal))]
    pal[0].save(path, save_all=True, append_images=pal[1:], duration=durations, loop=0, optimize=True)


def save_png(img, path, width=None):
    if width and img.width > width:
        img = img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)
    img.save(path, optimize=True)


def find_patient_with_session(b):
    """시연 환자 계정 중 지금 세션이 진행 중인 사람을 찾는다 (환자 화면에 '세션 없음'만 찍히지 않게).
    비밀번호를 바꾼 계정은 건너뛴다. 실패가 쌓이면 계정이 잠기므로(5회) 같은 계정을 다시 시도하지 않는다"""
    usable = None
    for email in ("patient2@nuada.test", "patient3@nuada.test", "patient@nuada.test"):
        if not b.login(email, required=False):
            continue
        usable = usable or email
        if b.js("fetch('/api/me/patient').then(r => r.json()).then(s => !!s.arm)"):
            return True
    if not usable:
        raise SystemExit("시연 비밀번호로 로그인되는 환자 계정이 없습니다")
    b.login(usable)                                  # 세션 중인 환자가 없으면 아무나 ('세션 없음' 화면)
    return False


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    if sys.platform == "win32":                     # 이 스크립트도 낮은 우선순위로 (사진 변환이 재생기를 밀어내지 않게)
        import ctypes
        ctypes.windll.kernel32.SetPriorityClass(ctypes.windll.kernel32.GetCurrentProcess(), 0x4000)  # BELOW_NORMAL
    b = Browser()
    try:
        frames, hold, full = record_dashboard(b)
        save_gif(frames, hold, OUT / "demo.gif")
        save_png(full, OUT / "dashboard.png", width=1400)

        b.size(400, 860, mobile=True, scale=2)       # 환자는 휴대폰으로 본다고 가정
        find_patient_with_session(b)                 # 찾은 환자로 로그인된 상태가 된다
        b.goto("/patient.html", settle=3)
        save_png(b.shot(full=True), OUT / "patient-mobile.png", width=600)

        b.size(1100, 900)
        b.login("guardian@nuada.test")
        b.goto("/guardian.html", settle=3)
        save_png(b.shot(full=True), OUT / "guardian.png", width=1100)
    finally:
        b.close()
    for p in sorted(OUT.iterdir()):
        print(f"{p.relative_to(ROOT)}  {p.stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    sys.exit(main())
