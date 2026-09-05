export type DeskRole = "ADMIN" | "APPROVER" | "OPERATOR";

export type Session = {
  token: string;
  userId: string;
  email: string;
  displayName: string;
  role: DeskRole;
};

const KEY = "recovery-desk-session";

export function getSession(): Session | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Session;
    if (!parsed?.token || !parsed.role) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function setSession(session: Session | null) {
  if (typeof window === "undefined") {
    return;
  }
  if (!session) {
    window.localStorage.removeItem(KEY);
    return;
  }
  window.localStorage.setItem(KEY, JSON.stringify(session));
}

export function homeFor(role: DeskRole) {
  if (role === "ADMIN") {
    return "/dashboard";
  }
  if (role === "APPROVER") {
    return "/approvals";
  }
  return "/desk";
}

export function roleLabel(role: DeskRole) {
  if (role === "ADMIN") {
    return "CEO · Admin";
  }
  if (role === "APPROVER") {
    return "Policy guard";
  }
  return "Recovery desk";
}
