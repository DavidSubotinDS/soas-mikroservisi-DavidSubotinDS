import { useEffect, useState } from "react";
import { RefreshCw, Plus } from "lucide-react";
import { useAuth } from "../auth";
import { request } from "../api";
import {
  AccountView,
  Busy,
  CurrencySelect,
  Notice,
  PageHeading,
} from "../components";
import { CRYPTO, FIAT, validAmount, type Account } from "../types";

export function Accounts({ crypto = false }: { crypto?: boolean }) {
  const { user, credential } = useAuth();
  const admin = user?.role === "ADMIN";
  const path = crypto ? "/crypto-wallets" : "/bank-accounts";
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [email, setEmail] = useState("");
  const [currency, setCurrency] = useState(crypto ? "ETH" : "EUR");
  const [amount, setAmount] = useState("");
  const [operation, setOperation] = useState("create");
  async function load() {
    setLoading(true);
    setError("");
    try {
      setAccounts(
        admin
          ? await request<Account[]>(path, credential)
          : [await request<Account>(`${path}/me`, credential)],
      );
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, [path, credential, admin]);
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    setSuccess("");
    if (!validAmount(amount, true)) {
      setError("Enter a non-negative amount with up to 12 decimal places.");
      return;
    }
    setBusy(true);
    try {
      await request(path + "/holdings", credential, {
        method: operation === "create" ? "POST" : "PUT",
        body: JSON.stringify({ email, currency, amount }),
      });
      setSuccess("Holding saved successfully.");
      setAmount("");
      await load();
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow={admin ? "ACCOUNT MANAGEMENT" : "YOUR HOLDINGS"}
        title={
          crypto ? "Your digital asset desk." : "A home for your currencies."
        }
      >
        {admin
          ? "View all USER accounts and add or update their holdings."
          : `View your ${crypto ? "crypto assets" : "fiat balances"} and keep track of your available funds.`}
      </PageHeading>
      <Notice message={error} />
      <Notice message={success} success />
      <div className={admin ? "management-grid" : ""}>
        <section className="panel">
          <div className="panel-top">
            <h2>{crypto ? "Crypto wallets" : "Bank accounts"}</h2>
            <button
              className="icon-button"
              aria-label="Refresh balances"
              onClick={load}
              disabled={loading}
            >
              <RefreshCw size={17} />
            </button>
          </div>
          {loading ? (
            <Busy />
          ) : accounts.length ? (
            accounts.map((account) => (
              <AccountView key={account.email} account={account} />
            ))
          ) : (
            <p className="empty">
              No accounts to display. Create a USER to provision their default
              holdings.
            </p>
          )}
        </section>
        {admin && (
          <aside className="panel">
            <div className="panel-top">
              <h2>Manage a holding</h2>
              <Plus size={18} />
            </div>
            <form onSubmit={save}>
              <label>
                Operation
                <select
                  value={operation}
                  onChange={(e) => setOperation(e.target.value)}
                >
                  <option value="create">Add currency holding</option>
                  <option value="update">Update existing balance</option>
                </select>
              </label>
              <label>
                USER email
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  list="account-emails"
                />
                <datalist id="account-emails">
                  {accounts.map((a) => (
                    <option key={a.email}>{a.email}</option>
                  ))}
                </datalist>
              </label>
              <CurrencySelect
                label="Currency"
                value={currency}
                onChange={setCurrency}
                options={crypto ? CRYPTO : FIAT}
              />
              <label>
                New total balance
                <input
                  inputMode="decimal"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                />
              </label>
              <p className="field-note">
                Set the total balance held in this currency. Newly created USER
                accounts start with EUR 0 and ETH 0.
              </p>
              <button className="primary" disabled={busy}>
                {busy ? "Saving…" : "Save holding"}
              </button>
            </form>
          </aside>
        )}
      </div>
    </>
  );
}
