// ==========================================================
// 관제 대시보드
//  1) 처음: REST /api/dashboard/state 로 전체 상태를 받는다
//  2) 이후: WebSocket /ws/dashboard 로 오는 소식(arm, decision, error, control)으로 갱신
//  3) 버튼: 세션 시작/종료, 수동 재개, 초기화 → 서버가 조건을 다시 검사한다
// ==========================================================

const CONTROL_ROLES = ['OPERATOR', 'PRINCIPAL_INVESTIGATOR'];
const MAX_ROWS = 120;
let me, arm, chart;
let thresholds = { move: 0.75, quality: 0.6 };   // 서버 설정값으로 덮어씀 (renderConfig)
const errorsById = new Map();

const REASON_KO = {
  INVALID_FORMAT: '형식 오류', INVALID_FIELD: '칸 오류', UNSUPPORTED_VERSION: '버전 오류', OUT_OF_ORDER: '순서 오류',
  STALE_COMMAND: '오래된 명령', NO_ACTIVE_SESSION: '세션 없음', SUBJECT_MISMATCH: '피험자 불일치',
  NOT_PARTICIPATING: '참여 환자 아님', SIGNAL_QUALITY: '신호 품질', DECISION_MISMATCH: '판단 불일치', PACKET_LOSS: '연결 끊김',
};
const ERROR_KO = {
  ELECTRODE_CONTACT: '전극 접촉 불량', LINE_NOISE: '전원 잡음(60Hz)', MUSCLE_ARTIFACT: '근육 잡음',
  SIGNAL_QUALITY_LOW: '신호 품질 저하', PACKET_LOSS: '연결 끊김', STALE_COMMAND: '오래된 명령(지연)',
  DECISION_MISMATCH: '판단 불일치', INVALID_MESSAGE: '메시지 규격 위반',
};
const CONTROL_KO = {
  SESSION_START: '세션 시작', SESSION_END: '세션 종료', STOP: '정지', RESUME: '수동 재개',
  RESUME_DENIED: '재개 거부', RESET_REQUIRED: '초기화 필요', RESET: '초기화',
};

(async function init() {
  me = await requireRole(['OPERATOR', 'PRINCIPAL_INVESTIGATOR', 'RESEARCHER']);
  // 자동 로그아웃 예외 (사용자 결정): 측정 세션이 진행 중이면 조작이 없어도 로그인을 유지한다.
  // 팔이 움직이는 동안 관제 담당자가 로그아웃되면 정지·오류 경보를 못 볼 수 있기 때문
  setIdleKeepAlive(() => Boolean(arm && arm.sessionId !== null && arm.sessionId !== undefined));
  const canControl = CONTROL_ROLES.includes(me.role);
  document.querySelectorAll('.control').forEach(b => { if (!canControl) b.style.display = 'none'; });

  document.getElementById('btnResume').onclick = resume;
  document.getElementById('btnReset').onclick = reset;

  setupChart();
  setupChannelMap();
  await loadState();
  await loadPatients();
  connect();
  // "마지막 수신 n초 전" 표시를 매초 갱신 (서버 소식이 없어도 시간이 흐르는 게 보이게)
  setInterval(() => { if (arm && arm.lastMessageAgoMs !== null) { arm.lastMessageAgoMs += 1000; renderArmText(); } }, 1000);
  window.addEventListener('resize', () => arm && drawArm(document.getElementById('arm'), arm.angle, arm.state));
})();

// ───────────── 처음 상태 ─────────────

async function loadState() {
  const s = await api('/api/dashboard/state');
  renderArm(s.arm);
  renderSession(s.session);
  renderConfig(s.config);
  document.getElementById('decisions').replaceChildren();
  s.decisions.slice().reverse().forEach(d => addDecision(d, false));
  // 서버는 최근 판단을 최신순으로 주므로 [0]이 마지막 판단 → 새로고침 직후에도 지도가 바로 채워진다
  updateChannelMap(s.decisions[0] || null);
  errorsById.clear();
  s.errors.forEach(e => errorsById.set(e.id, e));
  renderErrors();
  document.getElementById('controls').replaceChildren();
  s.controls.slice().reverse().forEach(c => addControl(c, false));
  if (chart) {
    chart.data.labels = []; chart.data.datasets.forEach(ds => ds.data = []);
    s.decisions.slice().reverse().forEach(addChartPoint);
    chart.update('none');
  }
}

// ───────────── 실시간 연결 ─────────────

function connect() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  const ws = new WebSocket(`${proto}://${location.host}/ws/dashboard`);
  const conn = document.getElementById('conn');
  ws.onopen = () => { conn.textContent = '● 실시간 연결'; conn.className = 'conn on'; loadState().catch(() => {}); };
  ws.onclose = () => {
    conn.textContent = '● 실시간 끊김 (재연결 중)'; conn.className = 'conn off';
    setTimeout(connect, 2000);   // 서버가 다시 켜지면 자동으로 다시 붙음
  };
  ws.onmessage = (ev) => {
    const { type, data } = JSON.parse(ev.data);
    if (type === 'arm') { renderArm(data); if (data.sessionId === null) renderSession(null); }
    else if (type === 'decision') { addDecision(data, true); addChartPoint(data); chart && chart.update('none'); updateChannelMap(data); }
    else if (type === 'error') { errorsById.set(data.id, data); renderErrors(); }
    else if (type === 'control') {
      addControl(data, true);
      if (data.type === 'SESSION_START' || data.type === 'SESSION_END') { loadState().catch(() => {}); loadPatients().catch(() => {}); }
    }
  };
}

// ───────────── 로봇팔 ─────────────

function renderArm(a) {
  arm = a;
  drawArm(document.getElementById('arm'), a.angle, a.state);
  const badge = document.getElementById('armBadge');
  badge.replaceChildren(el('span', { class: `badge st-${a.state}` }, stateName(a.state)));
  renderArmText();

  const box = document.getElementById('stopReason');
  box.replaceChildren();
  if (a.stopReason) {
    box.append(el('div', { class: 'stop-reason' + (a.state === 'RESET_REQUIRED' ? ' bad' : '') }, '정지 사유: ', a.stopReason));
  }
  const canControl = CONTROL_ROLES.includes(me.role);
  document.getElementById('btnResume').disabled = !canControl || a.state !== 'STOPPED';
  document.getElementById('btnReset').disabled = !canControl || !(a.state === 'STOPPED' || a.state === 'RESET_REQUIRED');
  if (a.state !== 'STOPPED') document.getElementById('blockers').replaceChildren();
}

function renderArmText() {
  const a = arm;
  document.getElementById('armState').textContent = stateName(a.state);
  document.getElementById('armAngle').textContent = `${Number(a.angle).toFixed(0)}°`;
  document.getElementById('lastMsg').textContent =
    a.lastMessageAgoMs === null ? '아직 없음' : `${(a.lastMessageAgoMs / 1000).toFixed(1)}초 전`;
  document.getElementById('cleanText').textContent = `${a.consecutiveClean}개 (재개 기준 ${a.requiredClean})`;
  document.getElementById('cleanBar').style.width = `${Math.min(100, (a.consecutiveClean / a.requiredClean) * 100)}%`;
}

async function resume() {
  try {
    const r = await api('/api/dashboard/arm/resume', { method: 'POST' });
    const ul = document.getElementById('blockers');
    ul.replaceChildren(...r.blockers.map(b => el('li', {}, b)));
    if (r.resumed) toast('재개했습니다. 서버가 조건을 확인했습니다.', true);
    else toast('재개 조건이 충족되지 않았습니다.');
  } catch (e) { toast(e.message); }
}

async function reset() {
  if (!confirm('팔을 기본 자세(90°)로 초기화합니다. 초기화 뒤에도 재개는 다시 조건을 확인해야 합니다.')) return;
  try { await api('/api/dashboard/arm/reset', { method: 'POST' }); toast('초기화했습니다.', true); }
  catch (e) { toast(e.message); }
}

// ───────────── 세션 ─────────────

let patientsCache = [];

function renderSession(s) {
  const box = document.getElementById('sessionBox');
  const badge = document.getElementById('sessionBadge');
  box.replaceChildren();
  const canControl = CONTROL_ROLES.includes(me.role);
  if (s) {
    badge.replaceChildren(el('span', { class: 'badge st-RUNNING' }, '진행 중'));
    box.append(el('dl', { class: 'kv' },
      el('dt', {}, '세션'), el('dd', {}, `#${s.id}`),
      el('dt', {}, '환자'), el('dd', {}, s.patientCode),
      el('dt', {}, '장비'), el('dd', {}, s.deviceCode),
      el('dt', {}, '데이터'), el('dd', {}, `${s.sourceType} · BCI IV 2a 피험자 ${s.datasetSubject}`),
      el('dt', {}, '시작'), el('dd', {}, new Date(s.startedAt).toLocaleString('ko-KR')),
    ));
    if (canControl) {
      box.append(el('div', { class: 'row', style: 'margin-top:10px' },
        el('button', { class: 'danger', type: 'button', onclick: endSession }, '세션 종료')));
    }
  } else {
    badge.replaceChildren(el('span', { class: 'badge st-IDLE' }, '없음'));
    if (!canControl) { box.append(el('p', { class: 'muted' }, '진행 중인 세션이 없습니다.')); return; }
    const select = el('select', { id: 'patientSelect', 'aria-label': '세션을 시작할 환자' });
    patientsCache.filter(p => p.participating).forEach(p =>
      select.append(el('option', { value: p.id }, `${p.researchCode}${p.displayName ? ' · ' + p.displayName : ''}`)));
    if (!select.options.length) select.append(el('option', { value: '' }, '참여 중인 환자 없음'));
    box.append(
      el('p', { class: 'muted', style: 'margin:0 0 8px' }, '참여 중인 환자(연구 등록 + 동의)만 시작할 수 있습니다. 팔은 정지 상태로 시작합니다.'),
      el('div', { class: 'row' }, select, el('button', { class: 'primary', type: 'button', onclick: startSession }, '세션 시작')));
  }
}

async function startSession() {
  const id = document.getElementById('patientSelect').value;
  if (!id) return toast('참여 중인 환자가 없습니다.');
  try { await api('/api/dashboard/session/start', { method: 'POST', body: { patientId: Number(id) } }); await loadState(); }
  catch (e) { toast(e.message); }
}

async function endSession() {
  if (!confirm('세션을 종료합니다. 팔은 세션 없음 상태가 됩니다.')) return;
  try { await api('/api/dashboard/session/end', { method: 'POST' }); await loadState(); }
  catch (e) { toast(e.message); }
}

function renderConfig(c) {
  thresholds = { move: c.moveThreshold, quality: c.minSignalQuality };   // 그래프 기준선
  const dl = document.getElementById('config');
  dl.replaceChildren(
    el('dt', {}, 'MOVE 확신도'), el('dd', {}, `≥ ${c.moveThreshold}`),
    el('dt', {}, '신호 품질'), el('dd', {}, `≥ ${c.minSignalQuality}`),
    el('dt', {}, '명령 유효시간'), el('dd', {}, `${c.staleCommandMs} ms`),
    el('dt', {}, '워치독 정지'), el('dd', {}, `${c.watchdogStopMs} ms`),
    el('dt', {}, '초기화 필요'), el('dd', {}, `${c.watchdogResetMs} ms`),
    el('dt', {}, '재개 조건'), el('dd', {}, `연속 정상 ${c.resumeRequiredClean}개`),
  );
}

// ───────────── 판단 기록 ─────────────

function addDecision(d, highlight) {
  const tbody = document.getElementById('decisions');
  const tr = el('tr', { class: highlight ? 'new' : '' },
    el('td', { class: 'mono' }, fmtTime(d.receivedAt)),
    el('td', { class: 'mono' }, d.seq ?? ''),
    el('td', {}, d.intent === 'left_hand' ? '왼손' : d.intent === 'right_hand' ? '오른손' : ''),
    el('td', { class: 'mono' }, fmtNum(d.confidence)),
    el('td', { class: 'mono' }, fmtNum(d.signalQuality, 2)),
    el('td', {}, el('span', { class: `badge r-${d.result}` }, d.result)),
    el('td', { class: 'wrap', title: d.detail || '' },
      d.reason ? (REASON_KO[d.reason] || d.reason) : '',
      d.scenario ? el('span', { class: 'injected' }, ` [주입: ${d.scenario}]`) : ''),
    el('td', { class: 'mono' }, d.armAngleAfter === null ? '' : Number(d.armAngleAfter).toFixed(0) + '°'),
    el('td', { class: 'muted' }, d.trueLabel === 'left_hand' ? '왼손' : d.trueLabel === 'right_hand' ? '오른손' : ''),
  );
  tbody.prepend(tr);
  while (tbody.children.length > MAX_ROWS) tbody.lastChild.remove();
}

// ───────────── 오류 + RAG ─────────────

function renderErrors() {
  const box = document.getElementById('errors');
  const list = [...errorsById.values()].sort((a, b) => b.id - a.id).slice(0, 20);
  if (!list.length) { box.replaceChildren(el('p', { class: 'muted' }, '기록된 장비 오류가 없습니다.')); return; }
  box.replaceChildren(...list.map(e => {
    const open = !e.resolvedAt;
    const card = el('article', { class: 'err' + (open ? ' open' : '') },
      el('header', {},
        el('b', {}, ERROR_KO[e.errorType] || e.errorType),
        el('span', { class: `badge ${open ? 'r-BLOCKED' : 'r-MOVE'}` }, open ? '미해결' : '재개로 해결'),
        e.injected ? el('span', { class: 'badge injected' }, '시나리오 주입') : null,
        el('span', { class: 'muted mono', style: 'font-size:12px' }, `${fmtTime(e.occurredAt)} · ${e.occurrenceCount}회`)),
      el('div', { class: 'detail' }, e.detail || ''));
    card.append(renderGuide(e));
    return card;
  }));
}

function renderGuide(e) {
  if (e.ragStatus === 'PENDING') return el('div', { class: 'guide' }, 'RAG 점검 안내를 받는 중…');
  if (e.ragStatus === 'FAILED') return el('div', { class: 'guide fail' }, 'RAG 안내 실패: ', e.ragAnswer || '');
  let sources = [];
  try { sources = JSON.parse(e.ragSourcesJson || '[]'); } catch { sources = []; }
  return el('div', {},
    el('div', { class: 'guide' }, e.ragAnswer || ''),
    el('div', { class: 'row', style: 'margin-top:6px;font-size:12px' },
      el('span', { class: `badge ${e.ragEvidenceSufficient ? 'r-MOVE' : 'r-HELD'}` },
        e.ragEvidenceSufficient ? '근거 충분' : '근거 부족 — 매뉴얼 직접 확인')),
    sources.length ? el('ol', { class: 'sources' },
      ...sources.map(s => el('li', {}, `${s.doc} › ${s.section}`, s.score !== null ? ` (유사도 ${Number(s.score).toFixed(2)})` : ''))) : null);
}

// ───────────── 관제 동작 기록 ─────────────

function addControl(c, highlight) {
  const tbody = document.getElementById('controls');
  tbody.prepend(el('tr', { class: highlight ? 'new' : '' },
    el('td', { class: 'mono' }, fmtTime(c.occurredAt)),
    el('td', {}, CONTROL_KO[c.type] || c.type),
    el('td', {}, c.actor),
    el('td', { class: 'wrap' }, c.detail || '')));
  while (tbody.children.length > 60) tbody.lastChild.remove();
}

// ───────────── 환자 (역할마다 보이는 범위가 다름) ─────────────

async function loadPatients() {
  patientsCache = await api('/api/patients');
  const note = {
    OPERATOR: '등록된 환자 · 익명 코드만', PRINCIPAL_INVESTIGATOR: '등록 환자는 실명 · 등록/배정 가능',
    RESEARCHER: '동의 환자만 · 배정 환자만 실명',
  }[me.role];
  document.getElementById('patientsNote').textContent = note;
  let researchers = [];
  if (me.role === 'PRINCIPAL_INVESTIGATOR') researchers = await api('/api/researchers');

  const isPi = me.role === 'PRINCIPAL_INVESTIGATOR';
  renderPatientTools(isPi);

  const tbody = document.getElementById('patients');
  tbody.replaceChildren(...patientsCache.map(p => {
    const actions = el('td', {});
    if (isPi) {
      if (!p.enrolled) {
        actions.append(el('button', { type: 'button', onclick: () => enroll(p.id) }, '연구 등록'));
      } else if (p.consent && researchers.length) {
        const sel = el('select', { 'aria-label': '배정할 연구자' }, ...researchers.map(r => el('option', { value: r.id }, r.name)));
        actions.append(sel, ' ', el('button', { type: 'button', onclick: () => assign(p.id, sel.value) }, '배정'));
      }
      // 초대 관련: 가입 전이면 다시 초대, 언제나 보호자 초대 가능 (연구책임자만)
      if (p.invitePending) actions.append(' ', el('button', { type: 'button', onclick: () => reinvite(p.userId, p.researchCode) }, '다시 초대'));
      actions.append(' ', el('button', { type: 'button', onclick: () => showGuardianForm(p) }, '보호자 초대'));
      if (p.guardians && p.guardians.length) {
        actions.append(el('div', { class: 'muted', style: 'font-size:12px;margin-top:4px' }, '보호자: ',
          ...p.guardians.flatMap((g, i) => [i ? ', ' : '', `${g.name}(${RELATION_KO[g.relation] || g.relation})`,
            g.invitePending ? el('button', { type: 'button', style: 'margin-left:4px;padding:1px 6px;font-size:11px',
              onclick: () => reinvite(g.userId, g.name) }, '다시 초대') : ''])));
      }
    }
    return el('tr', {},
      el('td', { class: 'mono' }, p.researchCode),
      el('td', {}, p.displayName || el('span', { class: 'muted' }, '비공개'),
        // 가입 전 환자는 로그인할 수 없으므로 상태를 보여 준다 (등록·동의와는 별개)
        p.invitePending ? el('span', { class: 'badge r-HELD', style: 'margin-left:6px' }, '초대 대기') : ''),
      el('td', {}, p.enrolled ? '✓' : '—'),
      el('td', {}, p.consent ? '✓' : '—'),
      el('td', {}, el('span', { class: `badge ${p.participating ? 'r-MOVE' : 'r-ABSTAIN'}` }, p.participating ? '참여 중' : '아님')),
      actions);
  }));
  // 세션이 없을 때 시작 목록도 새로 그림
  if (arm && arm.sessionId === null) renderSession(null);
}

// ───────────── 환자 등록·보호자 초대 (연구책임자, R4) ─────────────

const RELATION_KO = { FAMILY: '가족', CAREGIVER: '간병인', OTHER: '기타' };
const SIDE_KO = { LEFT_ARM: '왼팔', RIGHT_ARM: '오른팔', BOTH_ARMS: '양팔' };

/** 표 위의 입력 칸. 연구책임자가 아니면 비워 둔다 (서버도 403으로 막음) */
function renderPatientTools(isPi) {
  const box = document.getElementById('patientTools');
  if (!isPi) { box.replaceChildren(); return; }
  if (box.dataset.mode) return;   // 세션 시작·종료로 환자 목록을 다시 읽어도 입력 중인 칸이 지워지지 않게, 이미 그려져 있으면 그대로 둠
  showRegisterButton();
}

function showRegisterButton() {
  const box = document.getElementById('patientTools');
  box.dataset.mode = 'idle';
  box.replaceChildren(el('div', { class: 'row', style: 'margin-bottom:10px' },
    el('button', { type: 'button', class: 'primary', onclick: showRegisterForm }, '환자 등록')));
}

function showRegisterForm() {
  const box = document.getElementById('patientTools');
  box.dataset.mode = 'register';
  const name = el('input', { type: 'text', required: true, maxlength: 50, placeholder: '이름', 'aria-label': '환자 이름' });
  const email = el('input', { type: 'email', required: true, maxlength: 100, placeholder: '이메일', 'aria-label': '환자 이메일' });
  const side = el('select', { 'aria-label': '절단 부위' },
    ...Object.entries(SIDE_KO).map(([v, t]) => el('option', { value: v }, t)));
  const form = el('form', { class: 'row', style: 'margin-bottom:10px' }, name, email, side,
    el('button', { type: 'submit', class: 'primary' }, '등록하고 초대 링크 만들기'),
    el('button', { type: 'button', onclick: showRegisterButton }, '취소'));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const r = await api('/api/patients', { method: 'POST',
        body: { name: name.value.trim(), email: email.value.trim(), amputationSide: side.value } });
      showInvite(`환자 ${r.email}을(를) 등록했습니다. 연구 등록·동의는 이후 따로 진행합니다.`, r);
      showRegisterButton();
      await loadPatients();
    } catch (err) { toast(err.message); }
  });
  box.replaceChildren(el('p', { class: 'muted', style: 'font-size:12px;margin:0 0 6px' },
    '환자 계정을 만들고 초대 링크를 발급합니다. 환자가 링크로 들어와 직접 비밀번호를 정합니다.'), form);
  name.focus();
}

function showGuardianForm(p) {
  const box = document.getElementById('patientTools');
  box.dataset.mode = 'guardian';
  const name = el('input', { type: 'text', required: true, maxlength: 50, placeholder: '보호자 이름', 'aria-label': '보호자 이름' });
  const email = el('input', { type: 'email', required: true, maxlength: 100, placeholder: '보호자 이메일', 'aria-label': '보호자 이메일' });
  const relation = el('select', { 'aria-label': '관계' },
    ...Object.entries(RELATION_KO).map(([v, t]) => el('option', { value: v }, t)));
  // 신분 확인은 사람이 직접 하는 일이라, 확인했다는 "표시"를 반드시 받는다 (서버도 체크 없으면 거절)
  const checked = el('input', { type: 'checkbox', id: 'idChecked', required: true });
  const form = el('form', { class: 'row', style: 'margin-bottom:10px' }, name, email, relation,
    el('label', { for: 'idChecked', class: 'row', style: 'gap:4px;font-size:13px' }, checked, '신분을 확인했습니다'),
    el('button', { type: 'submit', class: 'primary' }, '초대 링크 만들기'),
    el('button', { type: 'button', onclick: showRegisterButton }, '취소'));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const r = await api(`/api/patients/${p.id}/guardians`, { method: 'POST',
        body: { name: name.value.trim(), email: email.value.trim(), relation: relation.value, identityChecked: checked.checked } });
      if (r.token) showInvite(`보호자 ${r.email}을(를) ${p.researchCode}에 연결했습니다.`, r);
      else toast('이미 가입한 보호자 계정이라 초대 없이 연결했습니다.', true);
      showRegisterButton();
      await loadPatients();
    } catch (err) { toast(err.message); }
  });
  box.replaceChildren(el('p', { class: 'muted', style: 'font-size:12px;margin:0 0 6px' },
    `${p.researchCode}${p.displayName ? ' · ' + p.displayName : ''} 환자의 보호자를 초대합니다.`), form);
  name.focus();
}

async function reinvite(userId, label) {
  if (!confirm(`${label}에게 초대 링크를 다시 만듭니다.\n이전에 보낸 링크는 더 이상 쓸 수 없습니다.`)) return;
  try {
    const r = await api(`/api/patients/invitations/${userId}`, { method: 'POST' });
    showInvite(`${r.email}의 초대 링크를 다시 만들었습니다.`, r);
    await loadPatients();
  } catch (err) { toast(err.message); }
}

/**
 * 초대 링크 상자. 비밀값은 주소의 # 뒤(fragment)에 넣는다:
 * # 뒤는 브라우저가 서버로 보내지 않아서, 링크를 여는 순간 서버 접속 기록에 비밀값이 남지 않는다.
 */
function showInvite(title, r) {
  const link = `${location.origin}/invite.html#${r.token}`;
  const box = document.getElementById('inviteResult');
  box.replaceChildren(
    el('b', {}, title),
    el('div', { class: 'row', style: 'margin:8px 0' },
      el('code', { class: 'temp-pw', style: 'font-size:12px;word-break:break-all' }, link),
      el('button', { type: 'button', onclick: async () => {
        try { await navigator.clipboard.writeText(link); toast('링크를 복사했습니다.', true); }
        catch { toast('복사하지 못했습니다. 직접 복사해 주세요.'); }
      }}, '복사')),
    el('div', { class: 'muted', style: 'font-size:12px' },
      '• 이 링크는 지금 한 번만 보입니다. 본인에게 안전하게 전달하세요.', el('br'),
      `• ${new Date(r.expiresAt).toLocaleString('ko-KR')}까지, 한 번만 쓸 수 있습니다.`),
    el('button', { type: 'button', style: 'margin-top:8px', onclick: () => { box.hidden = true; box.replaceChildren(); } }, '전달했어요 (닫기)'));
  box.hidden = false;
}

async function enroll(id) {
  try { await api(`/api/patients/${id}/enroll`, { method: 'POST' }); toast('연구에 등록했습니다.', true); await loadPatients(); }
  catch (e) { toast(e.message); }
}

async function assign(patientId, researcherId) {
  try {
    await api(`/api/patients/${patientId}/assignments`, { method: 'POST', body: { researcherId: Number(researcherId) } });
    toast('연구자를 배정했습니다.', true);
  } catch (e) { toast(e.message); }
}

// ───────────── 채널 상태 지도 ─────────────
// 채널 이름·순서는 재생기 ai-server-python/realtime/signal_quality.py 의 CHANNELS(BCI IV 2a 몽타주)와 같다.
// 좌표는 정확한 10-20 측정값이 아니라 보기 좋은 근사 배치 (viewBox 220×220, 위 = 코, 왼쪽 = 왼쪽 머리).
// 실제 머리에서도 앞-뒤 줄(FC, C, CP, P)이 대략 같은 간격이라, 줄마다 25px 간격 격자로 단순화했다.
const CHANNEL_POS = {
  Fz: [110, 45],
  FC3: [60, 75], FC1: [85, 75], FCz: [110, 75], FC2: [135, 75], FC4: [160, 75],
  C5: [35, 105], C3: [60, 105], C1: [85, 105], Cz: [110, 105], C2: [135, 105], C4: [160, 105], C6: [185, 105],
  CP3: [60, 135], CP1: [85, 135], CPz: [110, 135], CP2: [135, 135], CP4: [160, 135],
  P1: [85, 162], Pz: [110, 162], P2: [135, 162],
  POz: [110, 187],
};
// 왼손/오른손 상상은 C3·C4(와 Cz) 근처 신호로 판별하므로 늘 테두리로 강조 (signal_quality.py MOTOR_CHANNELS와 같음)
const MOTOR_CHANNELS = new Set(['C3', 'Cz', 'C4']);
const channelNodes = new Map();   // 채널 이름 → <g> 요소. 매번 새로 그리지 않고 class만 바꾸려고 보관

// common.js의 el()은 HTML 요소용이라, SVG 요소는 이름공간(namespace)을 지정해서 따로 만든다
function svgEl(tag, attrs = {}, ...children) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
  Object.entries(attrs).forEach(([k, v]) => node.setAttribute(k, v));
  children.forEach(c => node.append(c));
  return node;
}

function setupChannelMap() {
  const svg = document.getElementById('chanMap');
  svg.replaceChildren(
    svgEl('circle', { class: 'head', cx: 110, cy: 112, r: 96 }),
    svgEl('path', { class: 'nose', d: 'M100 18 L110 5 L120 18' }),
    svgEl('path', { class: 'ear', d: 'M14 98 Q4 112 14 126' }),
    svgEl('path', { class: 'ear', d: 'M206 98 Q216 112 206 126' }),
  );
  channelNodes.clear();
  Object.entries(CHANNEL_POS).forEach(([name, [x, y]]) => {
    // <title>: 마우스를 올리면 채널 이름이 뜬다 (작은 글자를 못 읽어도 확인 가능)
    const g = svgEl('g', { class: 'ch' + (MOTOR_CHANNELS.has(name) ? ' motor' : '') },
      svgEl('title', {}, name),
      svgEl('circle', { cx: x, cy: y, r: 10.5 }),
      svgEl('text', { x, y }, name));
    channelNodes.set(name, g);
    svg.append(g);
  });
}

/**
 * 마지막 판단 1개 기준으로 칠한다 (11/20 결정: 즉시 반응, 판단이 약 1초마다 오므로 깜빡임은 적음).
 *  - 신호 정보가 없는 판단(형식 오류, 연결 끊김 등)이면 회색: "정상"이라고 말할 근거가 없기 때문
 *  - 문제 채널 목록에 있으면 빨강, 없으면 초록
 *  - 문제 채널은 없는데 품질이 기준 미만이거나 원인이 있으면(60Hz·근육 잡음처럼 머리 전체 문제)
 *    점은 초록 그대로 두고 머리 윤곽만 주황으로 → "특정 채널이 아니라 전체 문제"를 그대로 보여 준다
 */
function updateChannelMap(d) {
  const svg = document.getElementById('chanMap');
  const info = document.getElementById('chanInfo');
  const hasSignal = d && d.signalQuality !== null && d.signalQuality !== undefined;
  const bad = new Set(hasSignal ? (d.badChannels || []) : []);

  channelNodes.forEach((g, name) => {
    g.classList.remove('ok', 'bad');
    if (hasSignal) g.classList.add(bad.has(name) ? 'bad' : 'ok');
  });

  const wholeHead = hasSignal && bad.size === 0 && (d.signalIssue || d.signalQuality < thresholds.quality);
  svg.classList.toggle('noise', Boolean(wholeHead));

  if (!d) { info.textContent = '아직 판단이 없습니다.'; info.className = 'chan-info muted'; return; }
  if (!hasSignal) {
    info.textContent = `seq ${d.seq ?? '-'} · 신호 정보 없음` + (d.reason ? ` (${REASON_KO[d.reason] || d.reason})` : '');
    info.className = 'chan-info muted';
    return;
  }
  const parts = [`품질 ${fmtNum(d.signalQuality, 2)}`];
  if (d.signalIssue) parts.push(ERROR_KO[d.signalIssue] || d.signalIssue);
  if (bad.size) parts.push(`문제 채널 ${[...bad].join(', ')}`);
  else if (wholeHead) parts.push('머리 전체 잡음 (특정 채널 아님)');
  else parts.push('모든 채널 정상');
  info.textContent = parts.join(' · ');
  info.className = 'chan-info ' + (bad.size ? 'r-BLOCKED' : wholeHead ? 'r-HELD' : 'r-MOVE');
}

// ───────────── 그래프 ─────────────

function setupChart() {
  if (typeof Chart === 'undefined') { document.getElementById('chartFallback').style.display = 'block'; return; }
  chart = new Chart(document.getElementById('chart'), {
    type: 'line',
    data: { labels: [], datasets: [
      { label: '확신도', data: [], borderColor: '#9ecbff', backgroundColor: '#9ecbff', tension: .25, pointRadius: 2, spanGaps: true },
      { label: '신호 품질', data: [], borderColor: '#d2a8ff', backgroundColor: '#d2a8ff', tension: .25, pointRadius: 2, spanGaps: true },
      { label: 'MOVE 기준', data: [], borderColor: '#3fb950', borderDash: [6, 4], pointRadius: 0 },
      { label: '품질 기준', data: [], borderColor: '#d29922', borderDash: [6, 4], pointRadius: 0 },
    ]},
    options: {
      animation: false, maintainAspectRatio: false,
      scales: { y: { min: 0, max: 1, ticks: { color: '#8b949e' }, grid: { color: '#21262d' } },
                x: { ticks: { color: '#8b949e', maxTicksLimit: 10 }, grid: { color: '#21262d' } } },
      plugins: { legend: { labels: { color: '#c9d1d9' } } },
    },
  });
}

function addChartPoint(d) {
  if (!chart || d.seq === null || d.seq === undefined) return;
  chart.data.labels.push(String(d.seq));
  chart.data.datasets[0].data.push(d.confidence);
  chart.data.datasets[1].data.push(d.signalQuality);
  chart.data.datasets[2].data.push(thresholds.move);
  chart.data.datasets[3].data.push(thresholds.quality);
  if (chart.data.labels.length > 60) { chart.data.labels.shift(); chart.data.datasets.forEach(ds => ds.data.shift()); }
}
