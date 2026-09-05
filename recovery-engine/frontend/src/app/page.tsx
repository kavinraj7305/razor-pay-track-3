"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { getSession, homeFor } from "@/lib/session";

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    const session = getSession();
    router.replace(session ? homeFor(session.role) : "/login");
  }, [router]);

  return (
    <div className="desk">
      <p className="wrap muted">Routing you to the right workspace…</p>
    </div>
  );
}
