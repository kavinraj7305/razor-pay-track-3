"use client";

import type { PlatformStatus } from "@/lib/types";

export function PlatformStrip({ status }: { status: PlatformStatus | null }) {
  const ready = status?.ready === true;
  return (
    <section className="platform-strip">
      <div className="ops-head">
        <div>
          <p className="pill">Platform</p>
          <strong>
            {status == null
              ? "Checking Redis, Kafka, and the case ledger…"
              : ready
                ? "Redis, Kafka, and the case ledger are connected."
                : "A platform service is down."}
          </strong>
        </div>
        <span className={`chip ${ready ? "go" : ""}`}>{ready ? "Live" : status == null ? "Checking" : "Check"}</span>
      </div>
      <div className="platform-grid">
        {(status?.components ?? PLACEHOLDERS).map((item) => (
          <article key={item.id} className={`platform-node ${item.connected ? "up" : status ? "down" : ""}`}>
            <div className="platform-node-head">
              <strong>{item.name}</strong>
              <span className={`badge ${item.connected ? "go" : ""}`}>{item.connected ? "Connected" : "Waiting"}</span>
            </div>
            <p>{item.role}</p>
            <span className="muted">{item.detail}</span>
          </article>
        ))}
      </div>
    </section>
  );
}

const PLACEHOLDERS = [
  { id: "redis", name: "Redis", connected: false, role: "Duplicate-event lock", detail: "One webhook, one case" },
  { id: "kafka", name: "Kafka", connected: false, role: "Payment event bus", detail: "payment · invoice · checkout" },
  { id: "postgres", name: "Postgres", connected: false, role: "Case ledger", detail: "Open book + audit" },
];
