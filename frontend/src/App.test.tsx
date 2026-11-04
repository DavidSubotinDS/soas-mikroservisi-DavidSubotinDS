import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { AuthProvider } from "./auth";
import App from "./App";

afterEach(() => vi.unstubAllGlobals());
beforeEach(() =>
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify(["EUR", "USD", "RSD", "NZD"])),
        ),
      ),
  ),
);
function mount(path = "/") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}
it("exposes public fiat and crypto pages without login", () => {
  mount();
  expect(
    screen.getByRole("heading", { name: "Your next currency move." }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("link", { name: "Crypto exchange" }),
  ).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Users" })).not.toBeInTheDocument();
});
it("redirects protected pages to login", () => {
  mount("/trade");
  expect(
    screen.getByRole("heading", { name: "Take a seat at the desk." }),
  ).toBeInTheDocument();
});
it.each(["OWNER", "ADMIN", "USER"])(
  "renders navigation for %s and clears credentials on logout",
  async (role) => {
    sessionStorage.setItem("soas.auth", "Basic demo");
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockImplementation((url: string) =>
          Promise.resolve(
            new Response(
              JSON.stringify(
                url.endsWith("/auth/me")
                  ? { id: 1, email: "test@soas.local", role }
                  : ["EUR", "USD", "RSD", "NZD"],
              ),
            ),
          ),
        ),
    );
    mount();
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Log out" }),
      ).toBeInTheDocument(),
    );
    expect(Boolean(screen.queryByRole("link", { name: "Users" }))).toBe(
      role !== "USER",
    );
    expect(Boolean(screen.queryByRole("link", { name: "Bank accounts" }))).toBe(
      role !== "OWNER",
    );
    expect(Boolean(screen.queryByRole("link", { name: "Trade assets" }))).toBe(
      role === "USER",
    );
    await userEvent.click(screen.getByRole("button", { name: "Log out" }));
    expect(sessionStorage.getItem("soas.auth")).toBeNull();
    expect(
      screen.queryByRole("link", { name: "Trade assets" }),
    ).not.toBeInTheDocument();
  },
);
it("blocks an OWNER from the trading page", async () => {
  sessionStorage.setItem("soas.auth", "Basic demo");
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementation((url: string) =>
        Promise.resolve(
          new Response(
            JSON.stringify(
              url.endsWith("/auth/me")
                ? { id: 1, email: "owner@soas.local", role: "OWNER" }
                : ["EUR", "USD", "RSD", "NZD"],
            ),
          ),
        ),
      ),
  );
  mount("/trade");
  await waitFor(() =>
    expect(
      screen.getByRole("heading", { name: "Your next currency move." }),
    ).toBeInTheDocument(),
  );
  expect(
    screen.queryByRole("heading", { name: "Make your next exchange." }),
  ).not.toBeInTheDocument();
});
