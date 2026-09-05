"use client";

import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { RecoveryDesk } from "@/components/RecoveryDesk";

export default function DeskPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO desk"
        title="Recovery desk"
        blurb="Create the failures and run the playbook. When policy stops a case, it waits for the other person."
      >
        <RecoveryDesk />
      </DeskChrome>
    </RoleGate>
  );
}
