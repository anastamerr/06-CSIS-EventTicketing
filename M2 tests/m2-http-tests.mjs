import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const userServiceUrl = withoutTrailingSlash(process.env.USER_SERVICE_URL ?? 'http://localhost:8081');
const eventServiceUrl = withoutTrailingSlash(process.env.EVENT_SERVICE_URL ?? 'http://localhost:8082');
const password = process.env.M2_TEST_PASSWORD ?? 'TestPwd!2026';

function withoutTrailingSlash(value) {
  return value.replace(/\/+$/, '');
}

function nonce() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function phone(seed) {
  const digits = seed.replace(/\D/g, '').slice(-8).padStart(8, '0');
  return `+2010${digits}`;
}

function userPayload(testCase) {
  const seed = nonce();
  return {
    name: `${testCase} User`,
    email: `${testCase.toLowerCase()}_${seed}@grader.testgen.io`,
    password,
    phone: phone(seed),
  };
}

async function request(baseUrl, method, path, body, token) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return {
    status: response.status,
    body: text ? JSON.parse(text) : null,
    text,
  };
}

function assert2xx(response) {
  assert.ok(response.status >= 200 && response.status <= 299, `expected 2xx, got ${response.status}: ${response.text}`);
}

function assert4xx(response) {
  assert.ok(response.status >= 400 && response.status <= 499, `expected 4xx, got ${response.status}: ${response.text}`);
}

async function register(testCase, overrides = {}) {
  const payload = { ...userPayload(testCase), ...overrides };
  const response = await request(userServiceUrl, 'POST', '/api/auth/register', payload);
  assert2xx(response);
  assert.equal(typeof response.body.id, 'number', 'register response must include numeric top-level id');
  return { payload, response, id: response.body.id, token: response.body.token };
}

async function login(email, loginPassword = password) {
  const response = await request(userServiceUrl, 'POST', '/api/auth/login', { email, password: loginPassword });
  return response;
}

test('official scenario snapshot is present and complete', async () => {
  const markdown = await readFile(new URL('./Event_Ticketing_Tests_Description.md', import.meta.url), 'utf8');
  const cases = new Set([...markdown.matchAll(/^\|\s*(\d+)\s*\|\s*TC\d{2,3}\s/mg)].map((match) => Number(match[1])));
  assert.equal(cases.size, 425);
  assert.ok(markdown.includes('TC01 — Register a new user'));
  assert.ok(markdown.includes('TC425 — DP-7 Adapter: M1 features using JPQL/DTO projection are exempt'));
});

test('TC01 register returns 2xx and numeric id', async () => {
  await register('TC01');
});

test('TC02 login with valid credentials returns a JWT-shaped token', async () => {
  const { payload } = await register('TC02');
  const response = await login(payload.email);

  assert2xx(response);
  assert.equal(typeof response.body.token, 'string');
  assert.equal(response.body.token.split('.').length, 3);
});

test('TC03 read own user profile with valid JWT returns a JSON object', async () => {
  const { payload, id } = await register('TC03');
  const loginResponse = await login(payload.email);
  assert2xx(loginResponse);

  const response = await request(userServiceUrl, 'GET', `/api/users/${id}`, undefined, loginResponse.body.token);
  assert2xx(response);
  assert.equal(typeof response.body, 'object');
  assert.ok(!Array.isArray(response.body));
});

test('TC04 duplicate email returns a clean 4xx', async () => {
  const { payload } = await register('TC04');
  const duplicate = {
    ...payload,
    name: 'TC04 Duplicate User',
    phone: phone(nonce()),
  };

  const response = await request(userServiceUrl, 'POST', '/api/auth/register', duplicate);
  assert4xx(response);
  assert.notEqual(response.status, 401);
  assert.notEqual(response.status, 403);
});

test('TC05 wrong password returns 401', async () => {
  const { payload } = await register('TC05');
  const response = await login(payload.email, 'WrongPwd!2026');

  assert.equal(response.status, 401);
});

test('TC06 valid JWT is accepted on non-user Event CRUD list', async () => {
  const { payload } = await register('TC06');
  const loginResponse = await login(payload.email);
  assert2xx(loginResponse);

  const response = await request(eventServiceUrl, 'GET', '/api/events', undefined, loginResponse.body.token);
  assert2xx(response);
});

test('TC07 missing Authorization on non-user Event CRUD list returns 401', async () => {
  const response = await request(eventServiceUrl, 'GET', '/api/events');
  assert.equal(response.status, 401);
});

test('TC331 preferences update merges new keys into existing JSON', async () => {
  const { payload, id } = await register('TC331', { preferences: { language: 'en' } });
  const loginResponse = await login(payload.email);
  assert2xx(loginResponse);

  const response = await request(
    userServiceUrl,
    'PUT',
    `/api/users/${id}/preferences`,
    { notifications: 'sms' },
    loginResponse.body.token,
  );

  assert2xx(response);
  assert.equal(response.body.preferences.language, 'en');
  assert.equal(response.body.preferences.notifications, 'sms');
});

test('TC333 deactivating an already deactivated user returns 400', async () => {
  const { payload, id } = await register('TC333');
  const loginResponse = await login(payload.email);
  assert2xx(loginResponse);

  const first = await request(userServiceUrl, 'PUT', `/api/users/${id}/deactivate`, {}, loginResponse.body.token);
  assert2xx(first);

  const second = await request(userServiceUrl, 'PUT', `/api/users/${id}/deactivate`, {}, loginResponse.body.token);
  assert.equal(second.status, 400);
});

test('TC335 profile DTO surfaces email and phone', async () => {
  const payload = userPayload('TC335');
  payload.email = `tc335_${nonce()}@et.io`;
  const registered = await register('TC335', payload);
  const loginResponse = await login(payload.email);
  assert2xx(loginResponse);

  const response = await request(
    userServiceUrl,
    'GET',
    `/api/users/${registered.id}/profile`,
    undefined,
    loginResponse.body.token,
  );

  assert2xx(response);
  assert.equal(response.body.email, payload.email);
  assert.equal(response.body.phone, payload.phone);
});

test('TC336 event search without token returns 401', async () => {
  const response = await request(eventServiceUrl, 'GET', '/api/events/search?status=AVAILABLE');
  assert.equal(response.status, 401);
});
