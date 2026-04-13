import { useEffect } from "react";
import { useBlocker } from "react-router-dom";

import { flushDraftSave } from "../store/draftListener";
import { useUnsavedChangesPrompt } from "./useUnsavedChangesPrompt";

export function useRouteLeavingGuard(when: boolean) {
    const { draftEnabled, promptOrProceed } = useUnsavedChangesPrompt();

    // When drafts are enabled, unsaved edits are auto-persisted — no need to block navigation,
    // but we must flush any pending debounced save before the route actually changes.
    const shouldBlock = when && !draftEnabled;

    const { proceed, reset, state } = useBlocker(
        ({ currentLocation, nextLocation }) => shouldBlock && currentLocation.pathname !== nextLocation.pathname,
    );

    // fallback for navigation outside router
    useEffect(() => {
        function listener(event: BeforeUnloadEvent) {
            if (!when) return;
            if (draftEnabled) {
                flushDraftSave();
                return;
            }
            // it causes browser alert on reload/close tab with default message that cannot be changed
            event.preventDefault(); // If you prevent default behavior in Mozilla Firefox prompt will always be shown
            event.returnValue = ""; // Chrome requires returnValue to be set
        }

        window.addEventListener("beforeunload", listener);
        return () => window.removeEventListener("beforeunload", listener);
    }, [when, draftEnabled]);

    // Flush pending draft save before in-app route changes too.
    useEffect(() => {
        if (!when || !draftEnabled) return;
        return () => flushDraftSave();
    }, [when, draftEnabled]);

    useEffect(() => {
        if (state === "blocked") {
            promptOrProceed(proceed, reset);
        }
    }, [promptOrProceed, proceed, reset, state]);
}

export function RouteLeavingGuard({ when }: { when?: boolean }): React.JSX.Element {
    useRouteLeavingGuard(when);
    return null;
}
