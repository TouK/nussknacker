import type { PendingPromise } from "../../../common/PendingPromise";

export type DelayPromise = PendingPromise<{ end: PendingPromise<void> }>;
