let testSnapshot = null;
let pollTimer = null;
let openDetail = null;
let activeSuite = 'unit';
const selected = { unit: new Set(), integration: new Set(), e2e: new Set() };

const SUITE_LABELS = {
    unit: 'Run unit tests',
    integration: 'Run regression tests',
    e2e: 'Run end-to-end tests'
};

function loadTests() {
    return fetch('/api/v1/tests')
        .then(response => response.json())
        .then(data => {
            testSnapshot = data;
            renderSuites();
            if (data.runningSuite) {
                schedulePoll();
            }
        })
        .catch(() => {
            document.getElementById('suite-sections').textContent = 'Could not load the test catalog.';
        });
}

function renderSuites() {
    const root = document.getElementById('suite-sections');
    const coverage = document.getElementById('coverage-panel');
    document.querySelectorAll('.suite-tab').forEach(button => {
        button.classList.toggle('active', button.dataset.suite === activeSuite);
    });
    if (activeSuite === 'coverage') {
        if (root) root.hidden = true;
        if (coverage) coverage.hidden = false;
        return;
    }
    if (coverage) coverage.hidden = true;
    if (root) root.hidden = false;
    if (!testSnapshot || !root) return;
    const busy = Boolean(testSnapshot.runningSuite);
    const queued = new Set(testSnapshot.queued || []);
    const scroll = {};
    root.querySelectorAll('.test-table-wrap').forEach(wrap => {
        scroll[wrap.closest('section').id] = wrap.scrollTop;
    });
    document.querySelectorAll('.suite-tab').forEach(button => {
        const suite = testSnapshot.suites.find(item => item.id === button.dataset.suite);
        if (!suite) return;
        const state = testSnapshot.runningSuite === suite.id
            ? '<span class="suite-tab-state">Running</span>'
            : queued.has(suite.id)
                ? '<span class="suite-tab-state">Queued</span>'
                : suite.failed
                    ? '<span class="suite-tab-state suite-tab-failed">' + suite.failed + ' failed</span>'
                    : '';
        button.innerHTML = esc(suite.title) + state;
    });
    const visible = testSnapshot.suites.find(item => item.id === activeSuite) || testSnapshot.suites[0];
    root.innerHTML = visible ? sectionHtml(visible, busy, queued.has(visible.id)) : '';
    root.querySelectorAll('.test-table-wrap').forEach(wrap => {
        wrap.scrollTop = scroll[wrap.closest('section').id] || 0;
    });
    root.querySelectorAll('.select-all').forEach(box => {
        const suite = testSnapshot.suites.find(item => item.id === box.dataset.suite);
        const count = suite.cases.filter(testCase => selected[suite.id].has(testCase.id)).length;
        box.checked = count > 0 && count === suite.cases.length;
        box.indeterminate = count > 0 && count < suite.cases.length;
    });
    root.querySelectorAll('.run-suite').forEach(button => {
        button.addEventListener('click', () => runSuite(button.dataset.suite));
    });
    root.querySelectorAll('.run-selected').forEach(button => {
        button.addEventListener('click', () => {
            const ids = [...selected[button.dataset.suite]];
            if (ids.length) runSuite(button.dataset.suite, ids);
        });
    });
    root.querySelectorAll('.select-all').forEach(box => {
        box.addEventListener('change', () => toggleSuite(box.dataset.suite, box.checked));
    });
    root.querySelectorAll('.select-case').forEach(box => {
        box.addEventListener('change', () => toggleCase(box.dataset.suite, box.value, box.checked));
    });
    root.querySelectorAll('.details-btn').forEach(button => {
        button.addEventListener('click', () => toggleDetail(button.dataset.suite, button.dataset.case));
    });
}

function toggleDetail(suiteId, caseId) {
    if (openDetail && openDetail.suiteId === suiteId && openDetail.caseId === caseId) {
        openDetail = null;
    } else {
        openDetail = { suiteId, caseId };
    }
    renderSuites();
}

function toggleSuite(suiteId, checked) {
    const suite = testSnapshot.suites.find(item => item.id === suiteId);
    selected[suiteId].clear();
    if (checked) suite.cases.forEach(testCase => selected[suiteId].add(testCase.id));
    renderSuites();
}

function toggleCase(suiteId, caseId, checked) {
    if (checked) selected[suiteId].add(caseId);
    else selected[suiteId].delete(caseId);
    renderSuites();
}

function sectionHtml(suite, busy, queued) {
    const runningThis = busy && testSnapshot.runningSuite === suite.id;
    const alertClass = suite.status === 'FAILED' ? 'alert-error' : suite.status === 'PASSED' ? 'alert-success' : 'alert-warning';
    const picked = suite.cases.filter(testCase => selected[suite.id].has(testCase.id)).length;
    const rows = orderedCases(suite.cases).map(({ testCase, index }) => {
        const failed = testCase.status === 'FAILED' || testCase.status === 'ERROR';
        const open = openDetail && openDetail.suiteId === suite.id && openDetail.caseId === testCase.id;
        const details = failed && testCase.where
            ? `<button type="button" class="btn btn-secondary details-btn" data-suite="${esc(suite.id)}" data-case="${esc(testCase.id)}">${open ? 'Hide' : 'Details'}</button>`
            : '';
        const panel = open ? `
            <tr class="failure-detail-row">
                <td colspan="8">
                    <div class="failure-detail" role="region" aria-label="Failure details for ${esc(testCase.name)}">
                        <h3>Where it failed</h3>
                        <p>${esc(testCase.where)}</p>
                        <h3>Why it failed</h3>
                        <p>${esc(testCase.why)}</p>
                        <h3>How to fix it</h3>
                        <p>${esc(testCase.fix)}</p>
                    </div>
                </td>
            </tr>` : '';
        const rowClass = testCase.status === 'RUNNING' ? 'row-running' : failed ? 'row-failed' : '';
        const parts = splitEvidence(testCase.observation);
        return `
        <tr class="${rowClass}">
            <td class="test-check" rowspan="2"><input type="checkbox" class="select-case" data-suite="${esc(suite.id)}" value="${esc(testCase.id)}" aria-label="Select ${esc(testCase.name)}" ${selected[suite.id].has(testCase.id) ? 'checked' : ''}></td>
            <td class="test-num" rowspan="2">${index + 1}</td>
            <td class="col-case">${esc(testCase.name)}<div class="test-id">${esc(testCase.id)}</div></td>
            <td>${esc(testCase.expected)}</td>
            <td><span class="badge ${badgeClass(testCase.status)}">${esc(label(testCase.status))}</span>${details}</td>
            <td class="test-time">${esc(testCase.started || '—')}</td>
            <td class="test-time">${esc(testCase.ended || '—')}</td>
            <td class="test-time">${formatTime(testCase.durationMs)}</td>
        </tr>
        <tr class="case-follow ${rowClass}">
            <td colspan="6">
                <div class="case-detail-grid">
                    <div><div class="case-field-label">Executed</div><div>${esc(parts.executed || '—')}</div></div>
                    <div><div class="case-field-label">Validated</div><div>${esc(parts.validated || '—')}</div></div>
                    <div><div class="case-field-label">Observed</div><div>${esc(parts.observed || '—')}</div></div>
                    <div><div class="case-field-label">Screenshot</div><div>${shotHtml(testCase) || '—'}</div></div>
                </div>
            </td>
        </tr>${panel}`;
    }).join('');
    return `
        <section class="suite-section" id="suite-${esc(suite.id)}">
            <div class="suite-toolbar">
                <button type="button" class="btn btn-primary run-suite" data-suite="${esc(suite.id)}" ${runningThis ? 'disabled' : ''}>
                    ${runningThis ? 'Running…' : queued ? 'Queued' : esc(SUITE_LABELS[suite.id])}
                </button>
                <button type="button" class="btn btn-secondary run-selected" data-suite="${esc(suite.id)}" ${picked === 0 ? 'disabled' : ''}>
                    ${queued && !runningThis ? 'Queued' : 'Run selected'}${picked ? ' (' + picked + ')' : ''}
                </button>
                <span class="result-meta">${suite.total} cases · ${suite.passed} passed · ${suite.failed} failed · ${esc(suite.status)}</span>
            </div>
            <p class="suite-blurb">${esc(suite.description || '')}</p>
            <div class="alert ${alertClass}">${esc(suite.observation || '')}</div>
            <div class="test-table-wrap">
                <table class="test-table">
                    <colgroup>
                        <col class="col-check">
                        <col class="col-num">
                        <col class="col-case">
                        <col>
                        <col class="col-result">
                        <col class="col-clock">
                        <col class="col-clock">
                        <col class="col-duration">
                    </colgroup>
                    <thead>
                    <tr>
                        <th class="test-check"><input type="checkbox" class="select-all" data-suite="${esc(suite.id)}" aria-label="Select all ${esc(suite.title)} cases"></th>
                        <th>#</th>
                        <th>Case</th>
                        <th>Expected result</th>
                        <th>Result</th>
                        <th>Started</th>
                        <th>Ended</th>
                        <th>Duration</th>
                    </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        </section>`;
}

function runSuite(suiteId, caseIds) {
    const options = { method: 'POST' };
    if (caseIds) {
        options.headers = { 'Content-Type': 'application/json' };
        options.body = JSON.stringify({ cases: caseIds });
    }
    fetch('/api/v1/tests/' + suiteId + '/run', options)
        .then(response => response.json().then(body => ({ ok: response.ok, body })))
        .then(({ ok, body }) => {
            if (!ok) {
                const suite = testSnapshot && testSnapshot.suites.find(item => item.id === suiteId);
                if (suite && body.observation) suite.observation = body.observation;
                renderSuites();
                return;
            }
            if (caseIds) caseIds.forEach(id => selected[suiteId].delete(id));
            else selected[suiteId].clear();
            testSnapshot = body;
            renderSuites();
            schedulePoll();
        })
        .catch(() => loadTests());
}

function schedulePoll() {
    if (pollTimer) return;
    pollTimer = setInterval(() => {
        fetch('/api/v1/tests')
            .then(response => response.json())
            .then(data => {
                testSnapshot = data;
                renderSuites();
                if (!data.runningSuite) {
                    clearInterval(pollTimer);
                    pollTimer = null;
                }
            });
    }, 800);
}

function orderedCases(cases) {
    return cases.map((testCase, index) => ({ testCase, index }))
        .sort((a, b) => caseRank(a.testCase.status) - caseRank(b.testCase.status) || a.index - b.index);
}

function caseRank(status) {
    return status === 'FAILED' || status === 'ERROR' ? 0 : 1;
}

function splitEvidence(observation) {
    const text = String(observation || '').trim();
    if (!text.startsWith('Executed:')) {
        return { executed: '', validated: '', observed: text === 'Not run in this session.' || text === 'Running' ? '' : text };
    }
    const validatedAt = text.indexOf(' Validated:');
    const observedAt = text.indexOf(' Observed:');
    const executed = stripPeriod(text.slice('Executed:'.length, validatedAt > 0 ? validatedAt : text.length));
    const validated = validatedAt < 0 ? '' : stripPeriod(text.slice(validatedAt + ' Validated:'.length, observedAt > validatedAt ? observedAt : text.length));
    const observed = observedAt < 0 ? '' : stripPeriod(text.slice(observedAt + ' Observed:'.length));
    return { executed, validated, observed };
}

function stripPeriod(value) {
    const trimmed = value.trim();
    return trimmed.endsWith('.') ? trimmed.slice(0, -1) : trimmed;
}

function shotHtml(testCase) {
    if (!testCase.screenshot) return '';
    const src = esc(testCase.screenshot);
    const label = 'Screenshot after ' + (testCase.name || 'the case');
    return `<a class="evidence-shot-link" href="${src}" target="_blank" rel="noopener"><img class="evidence-shot" src="${src}" alt="${esc(label)}"></a>`;
}

function badgeClass(status) {
    if (status === 'PASSED') return 'badge-complete';
    if (status === 'FAILED' || status === 'ERROR') return 'badge-failed';
    if (status === 'RUNNING') return 'badge-processing';
    return 'badge-pending';
}

function label(status) {
    if (status === 'NOT_RUN') return 'Not run';
    if (status === 'PASSED') return 'Passed';
    if (status === 'FAILED') return 'Failed';
    if (status === 'ERROR') return 'Error';
    if (status === 'SKIPPED') return 'Skipped';
    return status;
}

function formatTime(ms) {
    if (ms === null || ms === undefined) return '—';
    if (ms < 1000) return ms + ' ms';
    return (ms / 1000).toFixed(1) + ' s';
}

function esc(value) {
    return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

document.querySelectorAll('.suite-tab').forEach(button => {
    button.addEventListener('click', () => {
        activeSuite = button.dataset.suite;
        renderSuites();
    });
});

loadTests();
