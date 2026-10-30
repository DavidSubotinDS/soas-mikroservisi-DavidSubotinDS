import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { basicCredentials, request } from "./api";
import type { Identity } from "./types";

interface AuthContextValue {
  user: Identity | null;
  credential: string | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<Identity>;
  logout: () => void;
}
const Context = createContext<AuthContextValue | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [credential, setCredential] = useState<string | null>(() =>
    sessionStorage.getItem("soas.auth"),
  );
  const [user, setUser] = useState<Identity | null>(null);
  const [loading, setLoading] = useState(Boolean(credential));
  function logout() {
    sessionStorage.removeItem("soas.auth");
    setCredential(null);
    setUser(null);
  }
  useEffect(() => {
    if (!credential) {
      setLoading(false);
      return;
    }
    let active = true;
    request<Identity>("/auth/me", credential)
      .then((value) => {
        if (active) setUser(value);
      })
      .catch(() => {
        if (active) logout();
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [credential]);
  async function login(email: string, password: string) {
    const value = basicCredentials(email, password);
    const identity = await request<Identity>("/auth/me", value);
    sessionStorage.setItem("soas.auth", value);
    setCredential(value);
    setUser(identity);
    return identity;
  }
  return (
    <Context.Provider value={{ user, credential, loading, login, logout }}>
      {children}
    </Context.Provider>
  );
}
export const useAuth = () => {
  const auth = useContext(Context);
  if (!auth) throw new Error("AuthProvider is required");
  return auth;
};
