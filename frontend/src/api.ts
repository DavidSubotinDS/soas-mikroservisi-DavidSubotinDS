const baseUrl = import.meta.env.VITE_API_URL || "http://localhost:8765";
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export function basicCredentials(email: string, password: string) {
  const bytes = new TextEncoder().encode(`${email.trim()}:${password}`);
  return `Basic ${btoa(Array.from(bytes, (byte) => String.fromCharCode(byte)).join(""))}`;
}
export async function request<T>(
  path: string,
  auth: string | null = null,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (auth) headers.set("Authorization", auth);
  if (options.body) headers.set("Content-Type", "application/json");
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...options,
      headers,
      cache: "no-store",
    });
  } catch {
    throw new ApiError(
      0,
      "Cannot reach the exchange desk. Check that the API Gateway is running on port 8765.",
    );
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const data = await response.json();
      message = data.message || message;
    } catch {
      /* Server may return an empty error body. */
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204 || response.headers.get("content-length") === "0")
    return undefined as T;
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
