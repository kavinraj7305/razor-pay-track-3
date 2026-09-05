"use client";

import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { RecoveryDesk } from "@/components/RecoveryDesk";

export default function DeskPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO desk"
        title="Recovery desk"
        blurb="See why a payment failed, then start recovery."
      >
        <RecoveryDesk />
      </DeskChrome>
    </RoleGate>
  );
}
