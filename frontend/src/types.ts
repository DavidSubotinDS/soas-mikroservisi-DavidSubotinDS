export type Role = "OWNER" | "ADMIN" | "USER";
export interface Identity {
  id: number;
  email: string;
  role: Role;
}
export interface Holding {
  id: number;
  currency: string;
  amount: string | number;
}
export interface Account {
  email: string;
  holdings: Holding[];
}
export interface Rate {
  from: string;
  to: string;
  rate: number | string;
  provider: string;
}
export interface Transaction {
  from: string;
  to: string;
  quantity: number | string;
  received: number | string;
  bankAccount: Account | null;
  wallet: Account | null;
  report: string;
  operationId: string;
}
export const CRYPTO = ["BTC", "ETH", "SOL", "ADA", "XRP", "LTC", "DOGE", "BCH"];
export const formatAmount = (amount: string | number, crypto = false) =>
  new Intl.NumberFormat("en-GB", {
    maximumFractionDigits: crypto ? 12 : 4,
    minimumFractionDigits: crypto ? 0 : 2,
  }).format(Number(amount));
export function validAmount(value: string, allowZero = false) {
  return (
    /^\d{1,18}(\.\d{1,12})?$/.test(value) &&
    (allowZero ? Number(value) >= 0 : Number(value) > 0)
  );
}
