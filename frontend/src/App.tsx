import type { ReactNode } from "react";
import {
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import {
  ArrowUpRight,
  ArrowRightLeft,
  Banknote,
  Bitcoin,
  CircleUserRound,
  Coins,
  LogIn,
  LogOut,
  UsersRound,
  Wallet,
  Menu,
} from "lucide-react";
import { useAuth } from "./auth";
import { Busy } from "./components";
import type { Role } from "./types";
import { Rates } from "./pages/Rates";
import { Login } from "./pages/Login";
import { Accounts } from "./pages/Accounts";
import { Users } from "./pages/Users";
import { Transactions } from "./pages/Transactions";
import { useState } from "react";

export function Protected({
  roles,
  children,
}: {
  roles: Role[];
  children: ReactNode;
}) {
  const { user, loading } = useAuth();
  if (loading) return <Busy />;
  if (!user) return <Navigate to="/login" replace />;
  if (!roles.includes(user.role)) return <Navigate to="/" replace />;
  return children;
}
export default function App() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [menu, setMenu] = useState(false);
  const nav = (path: string, label: string, icon: ReactNode) => (
    <NavLink
      to={path}
      end
      className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
      onClick={() => setMenu(false)}
    >
      {icon}
      <span>{label}</span>
    </NavLink>
  );
  return (
    <div className="app-shell">
      <aside className={`sidebar ${menu ? "menu-open" : ""}`}>
        <NavLink to="/" className="brand">
          <span className="brand-mark">
            <ArrowRightLeft size={22} />
          </span>
          <div>
            SOAS<span>EXCHANGE DESK</span>
          </div>
        </NavLink>
        <div className="nav-section-label">EXPLORE</div>
        <nav aria-label="Public pages">
          {nav("/", "Fiat exchange", <Banknote size={19} />)}
          {nav("/crypto", "Crypto exchange", <Bitcoin size={19} />)}
        </nav>
        {user && (
          <>
            <div className="nav-section-label">YOUR WORKSPACE</div>
            <nav aria-label="Account pages">
              {["OWNER", "ADMIN"].includes(user.role) &&
                nav("/users", "Users", <UsersRound size={19} />)}
              {["ADMIN", "USER"].includes(user.role) && (
                <>
                  {nav("/bank", "Bank accounts", <Wallet size={19} />)}
                  {nav("/wallet", "Crypto wallets", <Coins size={19} />)}
                </>
              )}
              {user.role === "USER" && (
                <>
                  {nav(
                    "/convert",
                    "Convert fiat",
                    <ArrowRightLeft size={19} />,
                  )}
                  {nav("/trade", "Trade assets", <ArrowUpRight size={19} />)}
                </>
              )}
            </nav>
          </>
        )}
        <div className="sidebar-bottom">
          <div className="project-label">
            <span className="live-dot" /> SOAS · 2025 / 26
          </div>
          <p>
            A connected desk for
            <br />a world of currencies.
          </p>
          {user ? (
            <div className="profile">
              <CircleUserRound size={24} />
              <div>
                <strong>{user.email}</strong>
                <small>{user.role}</small>
              </div>
              <button aria-label="Log out" onClick={logout}>
                <LogOut size={17} />
              </button>
            </div>
          ) : (
            <NavLink to="/login" className="sidebar-login">
              <LogIn size={18} />
              Sign in to your workspace
              <ArrowUpRight size={16} />
            </NavLink>
          )}
        </div>
      </aside>
      <div className="main-shell">
        <div className="topbar">
          <button
            className="mobile-menu icon-button"
            aria-label="Toggle navigation"
            onClick={() => setMenu(!menu)}
          >
            <Menu size={20} />
          </button>
          <span>
            SOAS <span className="breadcrumb">/</span>{" "}
            {location.pathname === "/" || location.pathname === "/crypto"
              ? "Market overview"
              : "Workspace"}
          </span>
          <div className="topbar-right">
            <span className="academic-badge">University project</span>
            {user ? (
              <span className="session-tag">
                <span className="live-dot" />
                {user.role}
              </span>
            ) : (
              <NavLink to="/login">
                Sign in <ArrowUpRight size={15} />
              </NavLink>
            )}
          </div>
        </div>
        <main>
          <Routes>
            <Route path="/" element={<Rates key="fiat" />} />
            <Route path="/crypto" element={<Rates crypto key="crypto" />} />
            <Route
              path="/login"
              element={
                user ? (
                  <Navigate
                    to={user.role === "USER" ? "/bank" : "/users"}
                    replace
                  />
                ) : (
                  <Login />
                )
              }
            />
            <Route
              path="/users"
              element={
                <Protected roles={["OWNER", "ADMIN"]}>
                  <Users />
                </Protected>
              }
            />
            <Route
              path="/bank"
              element={
                <Protected roles={["ADMIN", "USER"]}>
                  <Accounts key="bank" />
                </Protected>
              }
            />
            <Route
              path="/wallet"
              element={
                <Protected roles={["ADMIN", "USER"]}>
                  <Accounts crypto key="wallet" />
                </Protected>
              }
            />
            <Route
              path="/convert"
              element={
                <Protected roles={["USER"]}>
                  <Transactions key="convert" />
                </Protected>
              }
            />
            <Route
              path="/trade"
              element={
                <Protected roles={["USER"]}>
                  <Transactions trade key="trade" />
                </Protected>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <footer>
          SOAS Exchange Desk
          <span>Servisno orijentisana arhitektura sistema · 2025/26</span>
        </footer>
      </div>
    </div>
  );
}
