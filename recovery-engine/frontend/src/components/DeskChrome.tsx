"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { logout, prettyError } from "@/lib/api";
import { getSession, homeFor, roleLabel, setSession, type DeskRole, type Session } from "@/lib/session";

const LINKS: { href: string; label: string; roles: DeskRole[] }[] = [
  { href: "/dashboard", label: "CEO dashboard", roles: ["ADMIN"] },
  { href: "/approvals", label: "Policy queue", roles: ["APPROVER"] },
  { href: "/desk", label: "Recovery desk", roles: ["ADMIN"] },
];

export function RoleGate({ allow, children }: { allow: DeskRole[]; children: ReactNode }) {
  const router = useRouter();
  const [ok, setOk] = useState(false);

  useEffect(() => {
    const session = getSession();
    if (!session) {
      router.replace("/login");
      return;
    }
    if (!allow.includes(session.role)) {
      router.replace(homeFor(session.role));
      return;
    }
    setOk(true);
  }, [allow, router]);

  if (!ok) {
    return (
      <div className="desk">
        <p className="wrap muted">Opening your workspace…</p>
      </div>
    );
  }
  return <>{children}</>;
}

export function DeskChrome({
  kicker,
  title,
  blurb,
  children,
}: {
  kicker: string;
  title: string;
  blurb: string;
  children: ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [session, setLocal] = useState<Session | null>(null);

  useEffect(() => {
    setLocal(getSession());
  }, []);

  async function signOut() {
    try {
      await logout();
    } catch {
      /* session already gone */
    }
    setSession(null);
    router.replace("/login");
  }

  return (
    <div className="desk">
      <nav className="role-nav">
        <div className="role-who">
          <p className="pill">{session ? roleLabel(session.role) : "…"}</p>
          <strong>{session?.displayName ?? "Signed in"}</strong>
        </div>
        <div className="role-links">
          {LINKS.filter((link) => session && link.roles.includes(session.role)).map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={pathname === link.href ? "role-link active" : "role-link"}
            >
              {link.label}
            </Link>
          ))}
        </div>
        <div className="role-out">
          <button className="ghost-btn" type="button" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </nav>
      <header className="desk-bar">
        <div>
          <p className="pill">{kicker}</p>
          <h1>{title}</h1>
          <p>{blurb}</p>
        </div>
      </header>
      {children}
    </div>
  );
}
