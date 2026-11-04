import { useEffect, useState } from "react";
import { request } from "./api";

export function useFiatCurrencies(enabled = true) {
  const [currencies, setCurrencies] = useState<string[]>([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    setLoading(true);
    setError("");
    void request<unknown>("/currency-exchange/assets")
      .then((data) => {
        if (
          !Array.isArray(data) ||
          !data.length ||
          data.some(
            (code) => typeof code !== "string" || !/^[A-Z]{3}$/.test(code),
          )
        ) {
          throw new Error(
            "Supported currencies could not be loaded. Please try again.",
          );
        }
        if (active) setCurrencies([...new Set(data as string[])].sort());
      })
      .catch((failure: Error) => {
        if (active) setError(failure.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [enabled, attempt]);

  return {
    currencies,
    loading: enabled && loading,
    error: enabled ? error : "",
    ready: !enabled || (!loading && !error && currencies.length > 0),
    reload: () => setAttempt((value) => value + 1),
  };
}
