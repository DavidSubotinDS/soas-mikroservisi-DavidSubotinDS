import { useState } from "react";
import { ArrowRightLeft } from "lucide-react";
import { useAuth } from "../auth";
import { request } from "../api";
import {
  CurrencySelect,
  CurrencyCatalogStatus,
  Notice,
  PageHeading,
  TransactionView,
} from "../components";
import { CRYPTO, validAmount, type Transaction } from "../types";
import { useFiatCurrencies } from "../currencies";

export function Transactions({ trade = false }: { trade?: boolean }) {
  const { credential } = useAuth();
  const [mode, setMode] = useState("buy");
  const catalog = useFiatCurrencies(!trade || mode !== "swap");
  const [from, setFrom] = useState("EUR");
  const [to, setTo] = useState(trade ? "BTC" : "RSD");
  const [quantity, setQuantity] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<Transaction | null>(null);
  function changeMode(value: string) {
    setMode(value);
    setFrom(value === "buy" ? "EUR" : "ETH");
    setTo(value === "sell" ? "EUR" : "BTC");
    setResult(null);
    setError("");
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    setResult(null);
    if (!validAmount(quantity)) {
      setError(
        "Enter a quantity greater than zero, with up to 12 decimal places.",
      );
      return;
    }
    setBusy(true);
    try {
      setResult(
        await request<Transaction>(
          `/${trade ? "trade-service" : "currency-conversion"}?${new URLSearchParams({ from, to, quantity })}`,
          credential,
        ),
      );
      setQuantity("");
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow={trade ? "CRYPTO TRADING" : "FIAT CONVERSION"}
        title={trade ? "Make your next exchange." : "Move between currencies."}
      >
        {trade
          ? "Buy crypto, sell into fiat, or exchange one digital asset for another."
          : "Convert available funds from one fiat currency into another. Your balances update after each successful exchange."}
      </PageHeading>
      <div className="transaction-grid">
        <section className="panel">
          <div className="panel-top">
            <h2>{trade ? "Trade assets" : "Convert fiat"}</h2>
            <ArrowRightLeft size={19} />
          </div>
          {trade && (
            <div className="segmented" aria-label="Trade category">
              {[
                ["buy", "Buy crypto"],
                ["sell", "Sell crypto"],
                ["swap", "Crypto swap"],
              ].map(([value, label]) => (
                <button
                  type="button"
                  className={mode === value ? "selected" : ""}
                  key={value}
                  onClick={() => changeMode(value)}
                >
                  {label}
                </button>
              ))}
            </div>
          )}
          <CurrencyCatalogStatus {...catalog} />
          <form onSubmit={submit}>
            <div className="pair-fields no-swap">
              <CurrencySelect
                label="From"
                value={from}
                onChange={setFrom}
                options={!trade || mode === "buy" ? catalog.currencies : CRYPTO}
              />
              <CurrencySelect
                label="To"
                value={to}
                onChange={setTo}
                options={
                  !trade || mode === "sell" ? catalog.currencies : CRYPTO
                }
              />
            </div>
            <label>
              Quantity of {from}
              <input
                inputMode="decimal"
                placeholder="0.00"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                required
              />
            </label>
            <p className="field-note">
              The exchange uses the latest available provider rate.{" "}
              {trade &&
                "Other fiat currencies are settled through USD automatically."}{" "}
              Submit once and wait for the result.
            </p>
            <Notice message={error} />
            <button className="primary" disabled={busy || !catalog.ready}>
              {busy
                ? "Processing exchange…"
                : trade
                  ? "Execute trade"
                  : "Convert funds"}
              <ArrowRightLeft size={17} />
            </button>
          </form>
        </section>
        <aside className="transaction-aside">
          {result ? (
            <TransactionView result={result} />
          ) : (
            <div className="empty-result">
              <span className="empty-icon">
                <ArrowRightLeft size={28} />
              </span>
              <h2>Your exchange, at a glance.</h2>
              <p>
                The transaction report and your updated holdings appear here
                after a successful exchange.
              </p>
              <small>Only available funds can be exchanged.</small>
            </div>
          )}
        </aside>
      </div>
    </>
  );
}
