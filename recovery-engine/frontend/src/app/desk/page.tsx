"use client";

import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { RecoveryDesk } from "@/components/RecoveryDesk";

export default function DeskPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO desk"
        title="Recovery desk"
        blurb="Pick a failed payment, see why it stalled, and start recovery."
      >
        <RecoveryDesk />
      </DeskChrome>
    </RoleGate>
  );
}
