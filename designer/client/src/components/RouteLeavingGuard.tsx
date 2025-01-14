import { useCallback, useEffect } from "react";
import { unsavedProcessChanges } from "../common/DialogMessages";
import { useWindows } from "../windowManager";
import { useBlocker } from "react-router-dom";

export function useRouteLeavingGuard(when: boolean) {
    const { confirm } = useWindows();

    const { proceed, reset, state } = useBlocker(
        ({ currentLocation, nextLocation }) => when && currentLocation.pathname !== nextLocation.pathname,
    );

    const showModal = useCallback(
        () =>
            confirm({
                text: unsavedProcessChanges(),
                onConfirmCallback: (confirmed) => (confirmed ? proceed() : reset()),
                confirmText: "DISCARD",
                denyText: "CANCEL",
            }),
        [confirm, proceed, reset],
    );

    // fallback for navigation outside router
    useEffect(() => {
        function listener(event: BeforeUnloadEvent) {
            if (when) {
                // it causes browser alert on reload/close tab with default message that cannot be changed
                event.preventDefault(); // If you prevent default behavior in Mozilla Firefox prompt will always be shown
                event.returnValue = ""; // Chrome requires returnValue to be set
            }
        }

        window.addEventListener("beforeunload", listener);
        return () => window.removeEventListener("beforeunload", listener);
    }, [when]);

    useEffect(() => {
        if (state === "blocked") {
            showModal();
        }
    }, [showModal, state]);
}

export function RouteLeavingGuard({ when }: { when?: boolean }): JSX.Element {
    useRouteLeavingGuard(when);
    return null;
}
