import type { ReactNode } from "react";
import { LoaderCircle, AlertCircle, CheckCircle2 } from "lucide-react";
import { CRYPTO, formatAmount, type Account, type Transaction } from "./types";

export function Notice({
  message,
  success = false,
}: {
  message: string;
  success?: boolean;
}) {
  return message ? (
    <div
      className={`notice ${success ? "success" : "error"}`}
      role={success ? "status" : "alert"}
    >
      {success ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
      <span>{message}</span>
    </div>
  ) : null;
}
export function Busy() {
  return (
    <div className="busy" role="status">
      <LoaderCircle className="spin" size={18} /> Loading…
    </div>
  );
}
export function PageHeading({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <header className="page-heading">
      <span className="eyebrow">{eyebrow}</span>
      <h1>{title}</h1>
      <p>{children}</p>
    </header>
  );
}
export function CurrencySelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: string[];
}) {
  return (
    <label>
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((code) => (
          <option key={code}>{code}</option>
        ))}
      </select>
    </label>
  );
}
export function AccountView({ account }: { account: Account }) {
  return (
    <div className="account-block">
      <div className="account-owner">{account.email}</div>
      {account.holdings.length ? (
        <div className="holdings">
          {account.holdings.map((holding) => (
            <div className="holding" key={holding.id}>
              <span
                className={`coin ${CRYPTO.includes(holding.currency) ? "crypto" : ""}`}
              >
                {holding.currency.slice(0, 1)}
              </span>
              <div>
                <strong>{holding.currency}</strong>
                <small>
                  {CRYPTO.includes(holding.currency)
                    ? "Crypto asset"
                    : "Fiat balance"}
                </small>
              </div>
              <b>
                {formatAmount(
                  holding.amount,
                  CRYPTO.includes(holding.currency),
                )}
              </b>
            </div>
          ))}
        </div>
      ) : (
        <p className="empty">No holdings yet.</p>
      )}
    </div>
  );
}
export function TransactionView({ result }: { result: Transaction }) {
  return (
    <section className="transaction-result">
      <Notice message={result.report} success />
      <div className="result-title">
        Updated {result.wallet ? "wallet" : "bank account"}
      </div>
      {(result.wallet || result.bankAccount) && (
        <AccountView account={(result.wallet || result.bankAccount)!} />
      )}
      <small className="operation">Reference · {result.operationId}</small>
    </section>
  );
}
