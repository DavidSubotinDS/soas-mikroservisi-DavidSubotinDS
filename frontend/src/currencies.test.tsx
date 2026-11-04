import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, expect, it, vi } from "vitest";
import { Rates } from "./pages/Rates";
import { Transactions } from "./pages/Transactions";
import { Accounts } from "./pages/Accounts";
import { AuthProvider } from "./auth";

afterEach(() => vi.unstubAllGlobals());
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status });
const currencies = ["EUR", "USD", "RSD", "NZD"];

it("uses the provider catalog for a public NZD quote", async () => {
  const fetch = vi
    .fn()
    .mockImplementation((url: string) =>
      Promise.resolve(
        url.endsWith("/assets")
          ? json(currencies)
          : json({ from: "EUR", to: "NZD", rate: 2, provider: "test" }),
      ),
    );
  vi.stubGlobal("fetch", fetch);
  render(<Rates />);
  await screen.findAllByRole("option", { name: "NZD" });
  await userEvent.selectOptions(screen.getByLabelText("To"), "NZD");
  await userEvent.click(
    screen.getByRole("button", { name: "Get exchange rate" }),
  );
  await waitFor(() =>
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/currency-exchange?from=EUR&to=NZD"),
      expect.anything(),
    ),
  );
  expect(
    screen.getByText("Source: test · Indicative market rate"),
  ).toBeInTheDocument();
});

it("disables fiat actions on catalog failure and recovers after retry", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValueOnce(
        json({ message: "Fiat provider unavailable" }, 503),
      )
      .mockResolvedValueOnce(json(currencies)),
  );
  render(<Rates />);
  await screen.findByRole("alert");
  expect(
    screen.getByRole("button", { name: "Get exchange rate" }),
  ).toBeDisabled();
  expect(screen.queryAllByRole("option", { name: "EUR" })).toHaveLength(0);
  await userEvent.click(
    screen.getByRole("button", { name: "Retry loading currencies" }),
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "Get exchange rate" }),
    ).toBeEnabled(),
  );
  expect(screen.getAllByRole("option", { name: "NZD" })).toHaveLength(2);
});

it.each(["convert", "buy", "sell", "admin"])(
  "offers provider fiat currencies for %s",
  async (page) => {
    const role = page === "admin" ? "ADMIN" : "USER";
    sessionStorage.setItem("soas.auth", "Basic demo");
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockImplementation((url: string) =>
          Promise.resolve(
            json(
              url.endsWith("/auth/me")
                ? { id: 1, email: "test@soas.local", role }
                : url.endsWith("/assets")
                  ? currencies
                  : url.endsWith("/bank-accounts/me")
                    ? { email: "test@soas.local", holdings: [] }
                    : [],
            ),
          ),
        ),
    );
    render(
      <MemoryRouter>
        <AuthProvider>
          {page === "admin" ? (
            <Accounts />
          ) : (
            <Transactions trade={page !== "convert"} />
          )}
        </AuthProvider>
      </MemoryRouter>,
    );
    if (page === "sell")
      await userEvent.click(
        screen.getByRole("button", { name: "Sell crypto" }),
      );
    await screen.findAllByRole("option", { name: "NZD" });
    const label =
      page === "admin" ? "Currency" : page === "sell" ? "To" : "From";
    await userEvent.selectOptions(screen.getByLabelText(label), "NZD");
    expect(screen.getByLabelText(label)).toHaveValue("NZD");
  },
);

it("keeps crypto lookup usable without the fiat provider", async () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  render(<Rates crypto />);
  expect(
    screen.getByRole("button", { name: "Get exchange rate" }),
  ).toBeEnabled();
  expect(fetch).not.toHaveBeenCalled();
});
