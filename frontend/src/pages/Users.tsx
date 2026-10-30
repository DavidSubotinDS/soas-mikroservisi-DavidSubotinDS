import { useEffect, useState } from "react";
import { Pencil, Trash2, Plus, X } from "lucide-react";
import { useAuth } from "../auth";
import { request } from "../api";
import { Busy, Notice, PageHeading } from "../components";
import type { Identity, Role } from "../types";

export function Users() {
  const { user, credential, logout } = useAuth();
  const owner = user?.role === "OWNER";
  const [users, setUsers] = useState<Identity[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [editing, setEditing] = useState<Identity | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("USER");
  const [deleting, setDeleting] = useState<Identity | null>(null);
  async function load() {
    setLoading(true);
    try {
      setUsers(await request<Identity[]>("/users", credential));
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, [credential]);
  function edit(value: Identity | null) {
    setEditing(value);
    setEmail(value?.email || "");
    setRole(value?.role || "USER");
    setPassword("");
    setError("");
    setSuccess("");
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      await request(`/users${editing ? `/${editing.id}` : ""}`, credential, {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify({ email, password: password || null, role }),
      });
      if (editing?.id === user?.id) {
        logout();
        return;
      }
      edit(null);
      setSuccess(
        editing
          ? "User updated."
          : "User created. USER accounts receive EUR 0 and ETH 0 automatically.",
      );
      await load();
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function remove() {
    if (!deleting) return;
    setBusy(true);
    setError("");
    try {
      await request(`/users/${deleting.id}`, credential, { method: "DELETE" });
      if (deleting.id === user?.id) {
        logout();
        return;
      }
      setDeleting(null);
      setSuccess("User and associated holdings deleted.");
      await load();
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading eyebrow="USER MANAGEMENT" title="The people at your desk.">
        {owner
          ? "Create, update and remove users across the platform."
          : "Create and update USER accounts. Bank accounts and wallets are provisioned automatically."}
      </PageHeading>
      <Notice message={error} />
      <Notice message={success} success />
      <div className="management-grid">
        <section className="panel">
          <div className="panel-top">
            <h2>
              Directory <span className="count">{users.length}</span>
            </h2>
            <span className="badge">
              {owner ? "Owner access" : "Admin access"}
            </span>
          </div>
          {loading ? (
            <Busy />
          ) : !users.length ? (
            <p className="empty">No users found.</p>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Email</th>
                    <th>Role</th>
                    <th>
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((value) => (
                    <tr key={value.id}>
                      <td>
                        <strong>{value.email}</strong>
                        {value.id === user?.id && <small>You</small>}
                      </td>
                      <td>
                        <span
                          className={`role-tag ${value.role.toLowerCase()}`}
                        >
                          {value.role}
                        </span>
                      </td>
                      <td>
                        <div className="row-actions">
                          {(owner || value.role === "USER") && (
                            <button
                              className="icon-button"
                              aria-label={`Edit ${value.email}`}
                              onClick={() => edit(value)}
                            >
                              <Pencil size={16} />
                            </button>
                          )}
                          {owner && (
                            <button
                              className="icon-button danger"
                              aria-label={`Delete ${value.email}`}
                              onClick={() => setDeleting(value)}
                            >
                              <Trash2 size={16} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
        <aside className="panel">
          <div className="panel-top">
            <h2>{editing ? "Edit user" : "Create a user"}</h2>
            {editing ? (
              <button
                className="icon-button"
                aria-label="Cancel editing"
                onClick={() => edit(null)}
              >
                <X size={18} />
              </button>
            ) : (
              <Plus size={19} />
            )}
          </div>
          <form onSubmit={save}>
            <label>
              Email
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
            <label>
              {editing ? "New password (optional)" : "Password"}
              <input
                type="password"
                autoComplete="new-password"
                required={!editing}
                minLength={8}
                maxLength={100}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <label>
              Role
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as Role)}
              >
                {(owner ? ["USER", "ADMIN", "OWNER"] : ["USER"]).map(
                  (value) => (
                    <option key={value}>{value}</option>
                  ),
                )}
              </select>
            </label>
            <p className="field-note">
              Only one OWNER may exist. Changing a USER to another role removes
              their bank and wallet holdings.
            </p>
            <button className="primary" disabled={busy}>
              {busy ? "Saving…" : editing ? "Save changes" : "Create user"}
            </button>
          </form>
        </aside>
      </div>
      {deleting && (
        <div className="modal-backdrop">
          <section
            className="modal panel"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="delete-title"
          >
            <h2 id="delete-title">Delete this user?</h2>
            <p>
              <strong>{deleting.email}</strong> and all their bank and wallet
              holdings will be removed.
            </p>
            {deleting.id === user?.id && (
              <p>You are deleting your own account and will be signed out.</p>
            )}
            <div className="modal-actions">
              <button
                className="secondary"
                onClick={() => setDeleting(null)}
                disabled={busy}
              >
                Cancel
              </button>
              <button
                className="danger-button"
                onClick={remove}
                disabled={busy}
              >
                {busy ? "Deleting…" : "Delete user"}
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
