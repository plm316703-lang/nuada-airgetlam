// ==========================================================
// 모든 화면이 함께 쓰는 도우미
//  - api(): 서버 호출. 로그인 쿠키는 브라우저가 자동으로 붙인다 (HttpOnly라 JS는 토큰을 못 봄)
//  - 401(로그인 필요)이면 로그인 화면으로, 403(권한 없음)이면 내 첫 화면으로 보낸다
// ==========================================================

async function api(path, options = {}) {
  const res = await fetch(path, {
    method: options.method || 'GET',
    headers: options.body ? { 'Content-Type': 'application/json' } : {},
    body: options.body ? JSON.stringify(options.body) : undefined,
    credentials: 'same-origin',
  });
  if (res.status === 401 && !path.startsWith('/api/auth/login')) {
    location.href = '/login.html';
    throw new Error('로그인이 필요합니다');
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const err = new Error((data && data.message) || ('요청 실패 ' + res.status));
    err.status = res.status;
    throw err;
  }
  return data;
}

/** 로그인 확인 + 역할 확인. 허용되지 않은 역할이면 자기 첫 화면으로 */
async function requireRole(allowed) {
  const me = await api('/api/auth/me');
  // 임시 비밀번호 상태면 어느 화면이든 비밀번호 변경부터 (서버도 다른 API를 403으로 막지만, 빈 화면 대신 바로 안내)
  if (me.mustChangePassword) {
    location.href = '/password.html';
    throw new Error('비밀번호 변경 필요');
  }
  if (!allowed.includes(me.role)) {
    location.href = me.home;
    throw new Error('권한 없음');
  }
  const who = document.getElementById('who');
  if (who) who.textContent = `${me.name} · ${roleName(me.role)}`;
  startIdleLogout(me.idleLogoutMinutes);
  const logout = document.getElementById('logout');
  if (logout) {
    logout.onclick = async () => { await api('/api/auth/logout', { method: 'POST' }); location.href = '/login.html'; };
    // 모든 역할 화면 머리글에 같은 버튼을 두려고, 화면마다 HTML을 고치는 대신 여기서 한 번에 붙인다
    if (!document.getElementById('pwLink')) {
      logout.before(el('button', { id: 'pwLink', type: 'button', onclick: () => { location.href = '/password.html'; } }, '비밀번호 변경'));
    }
  }
  return me;
}

// ==========================================================
// 자동 로그아웃 (R3, 사용자 결정: 30분 무조작, 1분 전 경고, 관제 화면은 세션 중 제외)
//  - 서버 증표 수명도 30분 → 화면을 우회하거나 PC를 꺼 둬도 서버 쪽에서 끝난다
//  - 사람이 조작하면(최대 5분에 한 번) /api/auth/refresh 로 증표를 새로 받아 쓰는 동안은 끊기지 않게 한다
//  - keepAlive(): 화면이 "지금은 조작이 없어도 로그인을 유지해야 한다"고 알려 주는 함수 (관제 화면이 세션 중일 때)
// ==========================================================
const IDLE = { minutes: 30, last: Date.now(), refreshedAt: 0, refreshEveryMs: 5 * 60 * 1000,
               keepAlive: () => false, bar: null, started: false };

function setIdleKeepAlive(fn) { IDLE.keepAlive = fn; }

function startIdleLogout(minutes) {
  if (IDLE.started || !minutes) return;
  IDLE.started = true;
  IDLE.minutes = minutes;
  // 갱신 간격은 증표 수명의 1/3 이하로 (최대 5분): 고정 5분이면 수명을 2분으로 줄여 시험할 때
  // 갱신 전에 증표가 먼저 끝나 버린다 (11/20 관제 화면 예외 확인 중 발견)
  IDLE.refreshEveryMs = Math.min(5 * 60 * 1000, (minutes * 60 * 1000) / 3);
  const touch = () => { IDLE.last = Date.now(); hideIdleWarning(); maybeRefresh(); };
  // passive: 스크롤 성능을 해치지 않게. mousemove는 너무 잦아서 maybeRefresh 안에서 갱신 주기로 걸러진다
  ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'].forEach(ev =>
    window.addEventListener(ev, touch, { passive: true }));
  setInterval(checkIdle, 5000);
  // 화면이 켜질 때 바로 한 번 갱신: 새로고침 직전에 받은 증표가 거의 끝나 가는 중일 수 있는데,
  // 화면은 그 증표가 언제 발급됐는지 모르므로 "지금 새로 받은 것"에서 시작해야 계산이 맞는다
  maybeRefresh();
}

async function maybeRefresh() {
  if (Date.now() - IDLE.refreshedAt < IDLE.refreshEveryMs) return;
  IDLE.refreshedAt = Date.now();
  try { await api('/api/auth/refresh', { method: 'POST' }); }
  catch { /* 증표가 이미 끝났으면 api()가 로그인 화면으로 보낸다 */ }
}

async function checkIdle() {
  if (IDLE.keepAlive()) {   // 예외 화면: 조작이 없어도 쓰는 중으로 본다
    IDLE.last = Date.now();
    hideIdleWarning();
    maybeRefresh();
    return;
  }
  const idleMs = Date.now() - IDLE.last;
  const limitMs = IDLE.minutes * 60 * 1000;
  if (idleMs >= limitMs) {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* 이미 끝났어도 로그인 화면으로 */ }
    location.href = '/login.html?reason=idle';
  } else if (idleMs >= limitMs - 60 * 1000) {
    showIdleWarning(Math.ceil((limitMs - idleMs) / 1000));
  }
}

function showIdleWarning(seconds) {
  if (!IDLE.bar) {
    IDLE.bar = el('div', { class: 'idle-warn', role: 'alertdialog', 'aria-live': 'assertive' },
      el('span', { class: 'idle-text' }),
      // 이 버튼을 누르는 것 자체가 click 조작이라 타이머가 초기화되고 증표도 갱신된다
      el('button', { class: 'primary', type: 'button', onclick: () => { IDLE.refreshedAt = 0; } }, '계속 사용하기'));
    document.body.append(IDLE.bar);
  }
  IDLE.bar.querySelector('.idle-text').textContent =
    `${IDLE.minutes}분 동안 조작이 없어 약 ${seconds}초 뒤 자동으로 로그아웃됩니다.`;
  IDLE.bar.style.display = 'flex';
}

function hideIdleWarning() {
  if (IDLE.bar) IDLE.bar.style.display = 'none';
}

function roleName(role) {
  return {
    PATIENT: '환자', GUARDIAN: '보호자', RESEARCHER: '연구자',
    PRINCIPAL_INVESTIGATOR: '연구책임자', OPERATOR: '관제 담당자', SYSTEM_ADMIN: '시스템 관리자',
  }[role] || role;
}

function stateName(state) {
  return { IDLE: '세션 없음', STOPPED: '정지', RUNNING: '동작 중', RESET_REQUIRED: '초기화 필요' }[state] || state;
}

/** 요소 만들기. 글자는 textContent로 넣어 HTML로 해석되지 않게 한다 (XSS 방지) */
function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

function fmtTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d)) return String(iso);
  const p = (n, w = 2) => String(n).padStart(w, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`;
}

function fmtNum(v, digits = 3) {
  return v === null || v === undefined ? '' : Number(v).toFixed(digits);
}

let toastTimer;
function toast(message, ok = false) {
  let t = document.querySelector('.toast');
  if (!t) { t = el('div', { class: 'toast', role: 'status' }); document.body.append(t); }
  t.textContent = message;
  t.classList.toggle('ok', ok);
  t.style.display = 'block';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.style.display = 'none'), 5000);
}

// ==========================================================
// 로봇팔 그리기 (Canvas 2D) — 어깨 고정, 위팔은 비스듬히, 팔꿈치 각도로 아래팔 회전
//   angle: 팔꿈치 굽힘 각도(도). 10 = 거의 폄, 150 = 많이 굽힘
//   state 색: RUNNING 초록 / STOPPED 주황 / RESET_REQUIRED 빨강 / IDLE 회색
// ==========================================================
function drawArm(canvas, angle, state) {
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (canvas.width !== Math.round(w * dpr)) { canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr); }
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);

  const color = { RUNNING: '#3fb950', STOPPED: '#d29922', RESET_REQUIRED: '#f85149', IDLE: '#6e7681' }[state] || '#6e7681';
  const s = Math.min(w / 400, h / 300);         // 화면 크기에 맞춘 배율 (기준 400×300)
  const rad = (deg) => (deg * Math.PI) / 180;
  const shoulder = { x: w * 0.22, y: h * 0.62 };
  const upperLen = 120 * s, foreLen = 110 * s;
  const upperDir = rad(15);                     // 위팔: 오른쪽으로 거의 수평 (살짝 아래)
  const elbow = { x: shoulder.x + upperLen * Math.cos(upperDir), y: shoulder.y + upperLen * Math.sin(upperDir) };
  // 아래팔: 굽힘 각도만큼 위팔 연장선에서 위쪽으로 회전 (화면 좌표는 y가 아래로 커서 빼기 = 위로)
  //   angle 0 = 일직선(폄), 90 = 직각, 150 = 많이 굽힘
  const flex = Math.max(10, Math.min(150, angle));
  const foreDir = upperDir - rad(flex);
  const wrist = { x: elbow.x + foreLen * Math.cos(foreDir), y: elbow.y + foreLen * Math.sin(foreDir) };

  // 움직일 수 있는 범위 (10°~150°) 점선 호
  ctx.strokeStyle = '#2a323d'; ctx.lineWidth = 2 * s; ctx.setLineDash([4 * s, 5 * s]);
  ctx.beginPath(); ctx.arc(elbow.x, elbow.y, foreLen, upperDir - rad(150), upperDir - rad(10));
  ctx.stroke(); ctx.setLineDash([]);

  // 받침대 (어깨가 붙은 기둥)
  ctx.fillStyle = '#21262d';
  ctx.fillRect(shoulder.x - 30 * s, shoulder.y - 40 * s, 18 * s, h - shoulder.y + 40 * s);

  // 팔 마디 (은색 그라데이션 = Airgetlám, 은의 팔)
  const segment = (a, b, width) => {
    const g = ctx.createLinearGradient(a.x, a.y - width, b.x, b.y + width);
    g.addColorStop(0, '#f0f3f6'); g.addColorStop(0.5, '#8b949e'); g.addColorStop(1, '#d0d7de');
    ctx.strokeStyle = g; ctx.lineCap = 'round'; ctx.lineWidth = width;
    ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
  };
  segment(shoulder, elbow, 26 * s);
  segment(elbow, wrist, 20 * s);

  // 관절 (상태 색 테두리)
  const joint = (p, r) => {
    ctx.fillStyle = '#0d1117'; ctx.strokeStyle = color; ctx.lineWidth = 4 * s;
    ctx.beginPath(); ctx.arc(p.x, p.y, r, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
  };
  joint(shoulder, 16 * s); joint(elbow, 13 * s);

  // 손(집게)
  ctx.strokeStyle = '#c9d1d9'; ctx.lineWidth = 5 * s; ctx.lineCap = 'round';
  const gripA = foreDir + 0.5, gripB = foreDir - 0.5;
  ctx.beginPath();
  ctx.moveTo(wrist.x, wrist.y); ctx.lineTo(wrist.x + 18 * s * Math.cos(gripA), wrist.y + 18 * s * Math.sin(gripA));
  ctx.moveTo(wrist.x, wrist.y); ctx.lineTo(wrist.x + 18 * s * Math.cos(gripB), wrist.y + 18 * s * Math.sin(gripB));
  ctx.stroke();
  joint(wrist, 8 * s);

  // 글자: 각도와 상태
  ctx.fillStyle = color; ctx.font = `600 ${16 * s}px system-ui, sans-serif`;
  ctx.fillText(`${stateName(state)}`, 12 * s, 24 * s);
  ctx.fillStyle = '#c9d1d9'; ctx.font = `${13 * s}px ui-monospace, Consolas, monospace`;
  ctx.fillText(`팔꿈치 ${Number(angle).toFixed(0)}°`, 12 * s, 44 * s);
  if (state === 'STOPPED' || state === 'RESET_REQUIRED') {
    ctx.fillStyle = color; ctx.font = `700 ${13 * s}px system-ui, sans-serif`;
    ctx.textAlign = 'right';
    ctx.fillText('■ 그 자리 정지', w - 12 * s, 22 * s);
    ctx.textAlign = 'left';
  }
}
