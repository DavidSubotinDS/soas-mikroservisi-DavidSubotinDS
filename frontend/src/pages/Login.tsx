import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowRight, LockKeyhole } from "lucide-react";
import { useAuth } from "../auth";
import { Notice, PageHeading } from "../components";

export function Login() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const user = await auth.login(email, password);
      navigate(user.role === "USER" ? "/bank" : "/users");
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading eyebrow="WELCOME BACK" title="Take a seat at the desk.">
        Sign in to manage your account and access the tools available for your
        role.
      </PageHeading>
      <div className="login-grid">
        <section className="panel">
          <div className="panel-top">
            <h2>Sign in</h2>
            <LockKeyhole size={20} />
          </div>
          <form onSubmit={submit}>
            <label>
              Email
              <input
                autoComplete="username"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@soas.local"
              />
            </label>
            <label>
              Password
              <input
                autoComplete="current-password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <Notice message={error} />
            <button className="primary" disabled={busy}>
              {busy ? "Signing in…" : "Sign in"}
              <ArrowRight size={18} />
            </button>
          </form>
        </section>
        <aside className="panel demo-panel">
          <span className="eyebrow">FOR THE PROJECT DEMO</span>
          <h2>Choose a role to explore.</h2>
          <p>Demo accounts are preloaded when the services start.</p>
          {[
            ["USER", "user", "User123!", "Balances, conversion & trading"],
            ["ADMIN", "admin", "Admin123!", "Users, bank accounts & wallets"],
            ["OWNER", "owner", "Owner123!", "Full user management"],
          ].map(([role, name, pass, description]) => (
            <button
              type="button"
              className="demo-role"
              key={role}
              onClick={() => {
                setEmail(`${name}@soas.local`);
                setPassword(pass);
              }}
            >
              <div>
                <strong>{role}</strong>
                <small>{description}</small>
              </div>
              <ArrowRight size={16} />
            </button>
          ))}
        </aside>
      </div>
    </>
  );
}
