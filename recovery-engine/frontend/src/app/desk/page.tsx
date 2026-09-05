"use client";

import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { RecoveryDesk } from "@/components/RecoveryDesk";

export default function DeskPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO desk"
        title="Recovery desk"
        blurb="Practice a failed payment here. Simulate one on the left, then Start. The first required step always runs. After that, extra silent retries can be skipped if they are unlikely to pay. Live customer payments do not need this page."
      >
        <RecoveryDesk />
      </DeskChrome>
    </RoleGate>
  );
}
