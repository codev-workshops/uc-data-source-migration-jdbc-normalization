import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// All inputs come from env vars; every one has a default so `k6 run` works with no overrides.
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PARAM_NAME = __ENV.PARAM_NAME || 'serviceImpl';
const VUS = parseInt(__ENV.VUS || '10', 10);
const RAMP_UP = __ENV.RAMP_UP || '30s';
const STEADY_DURATION = __ENV.STEADY_DURATION || '1m';
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';
const RESULTS_DIR = __ENV.RESULTS_DIR || 'perf-module/results';

const LOAN_ID = 'LN-2019-00142';
const BORROWER_ID = 'B-10001';

const IMPLS = ['legacy', 'normalized'];

// Legacy expands status codes to title case ("Active"); normalized returns the upper-case
// constant ("ACTIVE"). This is the observable signal that a request really hit the requested path.
const EXPECTED_LOAN_STATUS = { legacy: 'Active', normalized: 'ACTIVE' };

const ENDPOINTS = [
  { name: 'loans_list', path: '/api/loans' },
  { name: 'loan_by_id', path: `/api/loans/${LOAN_ID}` },
  { name: 'loan_payments', path: `/api/loans/${LOAN_ID}/payments` },
  { name: 'borrowers_list', path: '/api/borrowers' },
  { name: 'borrower_by_id', path: `/api/borrowers/${BORROWER_ID}` },
  { name: 'payments_by_loan', path: `/api/payments/loan/${LOAN_ID}` },
];

const latency = {
  legacy: new Trend('latency_legacy', true),
  normalized: new Trend('latency_normalized', true),
};

const endpointLatency = {};
for (const impl of IMPLS) {
  for (const endpoint of ENDPOINTS) {
    endpointLatency[`${impl}:${endpoint.name}`] = new Trend(
      `latency_${impl}_${endpoint.name}`,
      true,
    );
  }
}

export const options = {
  scenarios: {
    ramp_then_steady: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP, target: VUS },
        { duration: STEADY_DURATION, target: VUS },
        { duration: RAMP_DOWN, target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    latency_legacy: ['p(95)<500'],
    latency_normalized: ['p(95)<500'],
    'checks{check:routed to requested impl}': ['rate>0.99'],
    'checks{check:status is 200}': ['rate>0.99'],
    'checks{check:body is non-empty}': ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
};

function routedCorrectly(impl, endpoint, body) {
  if (endpoint.name !== 'loans_list' && endpoint.name !== 'loan_by_id') {
    return true;
  }
  try {
    const json = JSON.parse(body);
    const loan = Array.isArray(json)
      ? json.find((l) => l.loanAccountNumber === LOAN_ID)
      : json;
    return loan !== undefined && loan.status === EXPECTED_LOAN_STATUS[impl];
  } catch (e) {
    return false;
  }
}

export default function () {
  for (const endpoint of ENDPOINTS) {
    for (const impl of IMPLS) {
      const url = `${BASE_URL}${endpoint.path}?${PARAM_NAME}=${impl}`;
      const tags = { impl, endpoint: endpoint.name };
      const res = http.get(url, { tags });

      latency[impl].add(res.timings.duration, tags);
      endpointLatency[`${impl}:${endpoint.name}`].add(res.timings.duration, tags);

      check(
        res,
        {
          'status is 200': (r) => r.status === 200,
          'body is non-empty': (r) => r.body && r.body.length > 2,
          'routed to requested impl': (r) => routedCorrectly(impl, endpoint, r.body),
        },
        tags,
      );
    }
  }
}

function pct(metric, key) {
  return metric && metric.values ? metric.values[key] : undefined;
}

function fmt(value) {
  return value === undefined ? 'n/a' : value.toFixed(2);
}

function delta(legacyValue, normalizedValue) {
  if (legacyValue === undefined || normalizedValue === undefined) {
    return 'n/a';
  }
  const diff = normalizedValue - legacyValue;
  const percent = legacyValue === 0 ? 0 : (diff / legacyValue) * 100;
  return `${diff >= 0 ? '+' : ''}${diff.toFixed(2)} ms (${percent >= 0 ? '+' : ''}${percent.toFixed(1)}%)`;
}

function buildComparison(data) {
  const metrics = data.metrics;
  const rows = [];
  const header =
    'endpoint          | legacy p95  | normalized p95 | delta (norm - legacy) | legacy avg | normalized avg';
  rows.push('Legacy vs normalized latency (ms), per endpoint');
  rows.push('');
  rows.push(header);
  rows.push('-'.repeat(header.length));
  const json = {};
  for (const endpoint of ENDPOINTS) {
    const legacy = metrics[`latency_legacy_${endpoint.name}`];
    const normalized = metrics[`latency_normalized_${endpoint.name}`];
    const lp95 = pct(legacy, 'p(95)');
    const np95 = pct(normalized, 'p(95)');
    const lavg = pct(legacy, 'avg');
    const navg = pct(normalized, 'avg');
    json[endpoint.name] = {
      legacy: { avg: lavg, p95: lp95 },
      normalized: { avg: navg, p95: np95 },
      p95DeltaMs: lp95 !== undefined && np95 !== undefined ? np95 - lp95 : null,
    };
    rows.push(
      `${endpoint.name.padEnd(17)} | ${fmt(lp95).padStart(11)} | ${fmt(np95).padStart(14)} | ` +
        `${delta(lp95, np95).padStart(21)} | ${fmt(lavg).padStart(10)} | ${fmt(navg).padStart(14)}`,
    );
  }
  const overallLegacy = metrics.latency_legacy;
  const overallNormalized = metrics.latency_normalized;
  const lp95 = pct(overallLegacy, 'p(95)');
  const np95 = pct(overallNormalized, 'p(95)');
  rows.push('-'.repeat(header.length));
  rows.push(
    `${'ALL'.padEnd(17)} | ${fmt(lp95).padStart(11)} | ${fmt(np95).padStart(14)} | ` +
      `${delta(lp95, np95).padStart(21)} | ${fmt(pct(overallLegacy, 'avg')).padStart(10)} | ` +
      `${fmt(pct(overallNormalized, 'avg')).padStart(14)}`,
  );
  json.all = {
    legacy: { avg: pct(overallLegacy, 'avg'), p95: lp95 },
    normalized: { avg: pct(overallNormalized, 'avg'), p95: np95 },
    p95DeltaMs: lp95 !== undefined && np95 !== undefined ? np95 - lp95 : null,
  };
  rows.push('');
  rows.push(
    `Profile: 0 -> ${VUS} VUs over ${RAMP_UP}, hold ${STEADY_DURATION}, ${VUS} -> 0 over ${RAMP_DOWN}; ` +
      `param=${PARAM_NAME}; base=${BASE_URL}`,
  );
  return { text: rows.join('\n') + '\n', json };
}

export function handleSummary(data) {
  const comparison = buildComparison(data);
  const summaryText = textSummary(data, { indent: ' ', enableColors: false });
  return {
    stdout: summaryText + '\n' + comparison.text,
    [`${RESULTS_DIR}/summary.json`]: JSON.stringify(data, null, 2),
    [`${RESULTS_DIR}/summary.txt`]: summaryText,
    [`${RESULTS_DIR}/comparison.txt`]: comparison.text,
    [`${RESULTS_DIR}/comparison.json`]: JSON.stringify(comparison.json, null, 2),
  };
}
