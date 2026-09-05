"use client";

import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { RecoveryDesk } from "@/components/RecoveryDesk";

export default function DeskPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO desk"
        title="Recovery desk"
        blurb="Open a failed payment, see who is likely to pay, and run the playbook. Held cases wait for the other person."
      >
        <RecoveryDesk />
      </DeskChrome>
    </RoleGate>
  );
}
