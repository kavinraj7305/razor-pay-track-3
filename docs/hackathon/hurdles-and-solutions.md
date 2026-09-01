Created: **1 Sep 2026, 19:45 IST**
Reason: one page of real hurdles and how we solved them, written so we can say it to judges without a slide of fake problems.

Last updated: **1 Sep 2026, 19:55 IST**
Why updated: this file is failure recovery only — playbook gap; dropped the other bars and other breaks.

# Failure recovery

**What broke.** The playbook ran. That was the bug. Two `insufficient_funds` customers still got the **same** N retries. The folder knows the reason. It does not know who pays back.

**What we saw.** NSF → retry 3 times, every time. A customer who almost never recovers got the same chase as a customer who usually pays. Blind retries waste attempts. Good customers are not treated any differently.

**What we did.** We kept the playbook (reason still picks retry / pay-link / stop). We added labelled history + `P(recovery)` so the extra question is: should we retry **this customer**?

Say: “The playbook worked and still wasted retries. We did not rip it out. We scored the customer on top of it.”

