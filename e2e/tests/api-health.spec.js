// @ts-check
const { test, expect } = require("@playwright/test");

const API_BASE = "http://localhost:8080/api/v1";

test.describe("Backend API Health", () => {
  test("backend is reachable", async ({ request }) => {
    const resp = await request.get(`${API_BASE}/knowledge/graph`);
    expect(resp.status()).toBe(200);
  });

  test("register and login flow works", async ({ request }) => {
    const ts = Date.now();
    const email = `e2e-${ts}@test.com`;
    const password = "E2eTest1234!";

    // Register
    const regResp = await request.post(`${API_BASE}/auth/register`, {
      data: { email, password },
    });
    expect([200, 201, 409]).toContain(regResp.status());

    // Login
    const loginResp = await request.post(`${API_BASE}/auth/login`, {
      data: { email, password },
    });
    expect(loginResp.status()).toBe(200);
    const loginBody = await loginResp.json();
    expect(loginBody.token).toBeTruthy();
    expect(loginBody.expiresAt).toBeTruthy();
  });

  test("knowledge graph endpoint responds", async ({ request }) => {
    const resp = await request.get(`${API_BASE}/knowledge/graph`);
    expect(resp.status()).toBe(200);
  });
});
