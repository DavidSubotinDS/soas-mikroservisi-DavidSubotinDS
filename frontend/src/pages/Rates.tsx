import { useState } from "react";
import { ArrowDownUp, ArrowUpRight, Globe2, ShieldCheck } from "lucide-react";
import { request } from "../api";
import { CurrencySelect, Notice, PageHeading } from "../components";
import { CRYPTO, FIAT, formatAmount, type Rate } from "../types";

export function Rates({ crypto = false }: { crypto?: boolean }) {
  const [from, setFrom] = useState(crypto ? "BTC" : "EUR");
  const [to, setTo] = useState("USD");
  const [rate, setRate] = useState<Rate | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const assets = crypto ? [...CRYPTO, "USD", "EUR"] : FIAT;
  async function lookup(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setRate(null);
    try {
      setRate(
        await request<Rate>(
          `/${crypto ? "crypto" : "currency"}-exchange?${new URLSearchParams({ from, to })}`,
        ),
      );
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="THE EXCHANGE DESK"
        title={crypto ? "A window into crypto." : "Your next currency move."}
      >
        Explore {crypto ? "crypto market" : "global fiat"} rates before making
        your next exchange. Open to everyone, with no sign-in required.
      </PageHeading>
      <div className="rates-grid">
        <section className="panel rate-panel">
          <div className="panel-top">
            <h2>{crypto ? "Crypto" : "Fiat"} rate lookup</h2>
            <span className="badge">Public access</span>
          </div>
          <form onSubmit={lookup}>
            <div className="pair-fields">
              <CurrencySelect
                label="From"
                value={from}
                onChange={(value) => {
                  setFrom(value);
                  setRate(null);
                }}
                options={assets}
              />
              <button
                className="swap-button"
                type="button"
                aria-label="Swap currencies"
                onClick={() => {
                  setFrom(to);
                  setTo(from);
                  setRate(null);
                }}
              >
                <ArrowDownUp size={18} />
              </button>
              <CurrencySelect
                label="To"
                value={to}
                onChange={(value) => {
                  setTo(value);
                  setRate(null);
                }}
                options={assets}
              />
            </div>
            <button className="primary" disabled={busy}>
              {busy ? "Looking up rate…" : "Get exchange rate"}
              <ArrowUpRight size={18} />
            </button>
          </form>
          <Notice message={error} />
          <div className={`quote ${rate ? "has-rate" : ""}`} aria-live="polite">
            <span className="eyebrow">
              {rate ? "LATEST AVAILABLE RATE" : "MAKE YOUR FIRST LOOKUP"}
            </span>
            {rate ? (
              <>
                <div className="quote-base">1 {rate.from} =</div>
                <div className="quote-value">
                  {formatAmount(rate.rate, crypto)} <span>{rate.to}</span>
                </div>
                <small>Source: {rate.provider} · Indicative market rate</small>
              </>
            ) : (
              <>
                <div className="quote-placeholder">
                  Two currencies.
                  <br />
                  One clear rate.
                </div>
                <small>
                  Select a pair above to see its latest available rate.
                </small>
              </>
            )}
          </div>
        </section>
        <aside className="desk-note">
          <div className="orbit-art" aria-hidden="true">
            <div className="orbit o1" />
            <div className="orbit o2" />
            <span className="art-symbol">{crypto ? "₿" : "€"}</span>
            <span className="art-dot" />
            <span className="art-currency">{crypto ? "ETH" : "USD"}</span>
          </div>
          <span className="eyebrow">
            {crypto ? "DIGITAL ASSETS" : "MONEY WITHOUT BORDERS"}
          </span>
          <h2>
            {crypto
              ? "From Bitcoin to your next asset."
              : "A little clarity goes a long way."}
          </h2>
          <p>
            {crypto
              ? "Compare major cryptocurrencies with each other, or explore their value in euros and dollars."
              : "Compare familiar currencies and plan your exchanges, all from a single desk."}
          </p>
          <div className="note-detail">
            <Globe2 size={18} />
            <span>
              {crypto
                ? "Coinbase public market data"
                : "Frankfurter reference rates"}
            </span>
          </div>
          <div className="note-detail">
            <ShieldCheck size={18} />
            <span>Lookup only. Your balances stay unchanged.</span>
          </div>
        </aside>
      </div>
      <div className="info-strip">
        <span className="live-dot" />
        <strong>Built for the SOAS 2025/26 project</strong>
        <span>Fiat · Crypto · One exchange desk</span>
      </div>
    </>
  );
}
