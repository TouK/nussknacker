---
title: Processing Modes
---

# Processing modes
Nussknacker was created with stream processing in mind. However, it's also suitable for other use cases - for example, scenarios requiring on-demand request–response processing or batch execution (available in the future).

A **processing mode** defines how a [scenario](/docs/about/GLOSSARY.md#scenario) handles [records](/docs/about/GLOSSARY.md#record) - how they are received, processed, and produced.  

:::note
In this documentation, **record** is a neutral term used across all modes (streaming, batch, request–response).  
In streaming contexts, you may also see the term **event** - *that simply means a  record with a timestamp, used for time-based operations.*
:::

Currently there are two processing modes, described below.


## Streaming mode (stateful stream processing)

In streaming mode, a **scenario runs continuously** and consumes a flow of incoming events as they arrive. It produces outcomes in near real time - without anyone having to call it per decision. Think of it as an always-on decision flow.

Streaming mode is **stateful**: the scenario keeps a working memory (**state**) across events. That state can hold, for example, a customer’s running totals, the last seen status of an order, or a 30-minute window of activity. Thanks to this memory, the scenario can:

- aggregate over time (counts, sums, averages),
- correlate events (sessions, patterns),
- de-duplicate or throttle,
- look back at “what we already know” when a new event arrives.

It also **understands time**: it can group events into time windows and cope with events that show up late or out of order, so results stay meaningful even when data isn’t perfectly timed.

**How it contrasts with request–response**

- **Always on vs. on demand**: streaming runs continuously; request–response runs only when called.
- **Stateful vs. stateless by default**: streaming keeps memory between events; request–response forgets after each call (unless you store externally).
- **Event-driven vs. call-driven**: streaming reacts to new data; request–response reacts to a caller.
- **Many outputs vs. one reply**: streaming emits results whenever rules are met (immediately or at window ends); request–response returns exactly one answer per call.

**When to use**

- Ongoing monitoring and alerting (e.g., “notify if error rate > X in the last 5 minutes”).
- Per-customer/session logic that needs history (limits, quotas, churn signals).
- Real-time counters, rolling KPIs, and time-bucketed reports.
- Pattern detection (e.g., multiple failed logins followed by a high-value action).

Read [this document](/docs/scenariosAuthoring/streamingProcessingMode.md) for more details about the streaming processing mode. 

## Request-Response mode (instant decision)

In request–response mode, a **scenario acts like an instant-decision service**. A calling system sends input data (the **request**) and receives the decision or answer in the same call (the **response**). Nothing continues running after the reply.

By default it’s **stateless**: unless you explicitly write something to an external system, **nothing from one call is remembered for the next**. There’s no intermediate state kept beyond that single request.

**Key points**

- On demand: runs only when called.
- Immediate answer: one request in → one response out.
- Stateless by default: each call is independent unless you add external writes.
- Web analogy: like submitting a form and getting the result page right away.

**How it contrasts with streaming**

- **On demand vs. always on**: runs only when a caller asks; streaming runs continuously.
- **Stateless by default vs. stateful**: doesn’t keep memory between calls unless you store it externally; streaming keeps working memory across events.
- **One reply per call vs. many outputs over time**: returns exactly one response per call; streaming emits results as data arrives or windows close.
- **Caller-driven vs. data-driven**: activated by an external request; streaming reacts to incoming events.

**When to use**
- Instant eligibility/pricing/risk decisions returned to the caller.
- Lightweight “look up + rules + answer” flows for web/mobile/API calls.
- Situations where you don’t need history or time windows inside the scenario (any history can live in external systems you query).

Read [this document](/docs/scenariosAuthoring/requestResponseProcessingMode.mdx) for more details about the request - response processing mode.