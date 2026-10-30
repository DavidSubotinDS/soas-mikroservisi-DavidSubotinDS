import { afterEach, describe, expect, it, vi } from "vitest";
import { basicCredentials, request } from "./api";
import { validAmount } from "./types";

afterEach(() => vi.unstubAllGlobals());
describe("Gateway API", () => {
  it("uses Basic credentials and only the Gateway base URL", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ email: "user@soas.local", role: "USER" }),
        ),
      );
    vi.stubGlobal("fetch", fetch);
    const auth = basicCredentials("user@soas.local", "User123!");
    await request("/auth/me", auth);
    expect(fetch.mock.calls[0][0]).toBe("http://localhost:8765/auth/me");
    expect(
      (fetch.mock.calls[0][1].headers as Headers).get("Authorization"),
    ).toBe(auth);
  });
  it("shows the server business error", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ message: "Insufficient funds" }), {
            status: 400,
          }),
        ),
    );
    await expect(request("/trade-service")).rejects.toThrow(
      "Insufficient funds",
    );
  });
  it("handles network errors clearly", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new TypeError("Network error")),
    );
    await expect(request("/auth/me")).rejects.toThrow(
      "Cannot reach the exchange desk",
    );
  });
  it("handles no-content deletion responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
    );
    await expect(
      request("/users/1", null, { method: "DELETE" }),
    ).resolves.toBeUndefined();
  });
  it("validates amounts before sending a mutation", () => {
    expect(validAmount("0")).toBe(false);
    expect(validAmount("-1")).toBe(false);
    expect(validAmount("1e3")).toBe(false);
    expect(validAmount("100.001")).toBe(true);
    expect(validAmount("0", true)).toBe(true);
  });
});
