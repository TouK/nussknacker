import { useEffect } from "react";
import { useBlocker } from "react-router-dom";

import { hasUnpersistedDraftWrites } from "../store/draftListener";
import { useUnsavedChangesPrompt } from "./useUnsavedChangesPrompt";

export function useRouteLeavingGuard(when: boolean) {
    const { draftEnabled, promptOrProceed } = useUnsavedChangesPrompt();

    // Always intercept in-app navigation when there are unsaved changes; promptOrProceed will
    // either flush + proceed silently (draft is on and safely persisted) or fall back to the
    // DISCARD/CANCEL confirm dialog.
    const { proceed, reset, state } = useBlocker(
        ({ currentLocation, nextLocation }) => when && currentLocation.pathname !== nextLocation.pathname,
    );

    useEffect(() => {
        if (state === "blocked") {
            promptOrProceed(proceed, reset);
        }
    }, [promptOrProceed, proceed, reset, state]);

    // Browser-level navigation (refresh / close tab) — preventDefault triggers the native alert.
    // With drafts on we only alert when something hasn't been safely persisted yet.
    useEffect(() => {
        function listener(event: BeforeUnloadEvent) {
            if (!when) return;
            if (draftEnabled && !hasUnpersistedDraftWrites()) return;
            event.preventDefault();
            event.returnValue = "";
        }

        window.addEventListener("beforeunload", listener);
        return () => window.removeEventListener("beforeunload", listener);
    }, [when, draftEnabled]);
}

export function RouteLeavingGuard({ when }: { when?: boolean }): React.JSX.Element {
    useRouteLeavingGuard(when);
    return null;
}
