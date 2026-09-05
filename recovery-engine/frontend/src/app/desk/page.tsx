"use client";

import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { RecoveryDesk } from "@/components/RecoveryDesk";

export default function DeskPage() {
  return (
    <RoleGate allow={["OPERATOR", "ADMIN"]}>
      <DeskChrome
        kicker="Operator desk · add issues · run playbook"
        title="Recovery desk"
        blurb="Create failures and run the live process. You cannot assign roles or approve policy blocks — that is CEO and the policy guard."
      >
        <RecoveryDesk />
      </DeskChrome>
    </RoleGate>
  );
}
