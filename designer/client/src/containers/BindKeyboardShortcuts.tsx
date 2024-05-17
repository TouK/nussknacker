import { useCallback, useMemo } from "react";
import { useSelectionActions } from "../components/graph/SelectionContextProvider";
import { useDocumentListeners } from "./useDocumentListeners";
import { EventTrackingType, TrackEventParams, useEventTracking } from "./event-tracking";

export const isInputTarget = (target: EventTarget): boolean => ["INPUT", "SELECT", "TEXTAREA"].includes(target?.["tagName"]);
export const isInputEvent = (event: Event): boolean => isInputTarget(event?.target);

type KeyboardShortcutsMap = Record<string, (event: KeyboardEvent) => void>;

export function BindKeyboardShortcuts({ disabled }: { disabled?: boolean }): JSX.Element {
    const userActions = useSelectionActions();
    const { trackEvent } = useEventTracking();

    const eventWithStatistics = useCallback(
        (trackEventParams: TrackEventParams, event: unknown) => {
            trackEvent(trackEventParams);
            return event;
        },
        [trackEvent],
    );

    const keyHandlers: KeyboardShortcutsMap = useMemo(
        () => ({
            A: (e) => {
                if (e.ctrlKey || e.metaKey) {
                    eventWithStatistics({ type: EventTrackingType.KeyboardSelectAllNodes }, userActions.selectAll(e));
                    e.preventDefault();
                }
            },
            Z: (e) =>
                (e.ctrlKey || e.metaKey) && e.shiftKey
                    ? eventWithStatistics({ type: EventTrackingType.KeyboardRedoScenarioChanges }, userActions.redo(e))
                    : eventWithStatistics({ type: EventTrackingType.KeyboardUndoScenarioChanges }, userActions.undo(e)),
            DELETE: (e) => eventWithStatistics({ type: EventTrackingType.KeyboardDeleteNodes }, userActions.delete(e)),
            BACKSPACE: (e) => eventWithStatistics({ type: EventTrackingType.KeyboardDeleteNodes }, userActions.delete(e)),
            ESCAPE: (e) => eventWithStatistics({ type: EventTrackingType.KeyboardDeselectAllNodes }, userActions.deselectAll(e)),
        }),
        [eventWithStatistics, userActions],
    );

    const eventHandlers: KeyboardShortcutsMap = useMemo(
        () => ({
            keydown: (event) => {
                const keyHandler = keyHandlers?.[event.key.toUpperCase()];
                if (isInputEvent(event) || !keyHandler) return;
                return keyHandler(event);
            },
            copy: (event) =>
                userActions.copy ? eventWithStatistics({ type: EventTrackingType.KeyboardCopyNode }, userActions.copy(event)) : null,
            paste: (event) =>
                userActions.paste ? eventWithStatistics({ type: EventTrackingType.KeyboardPasteNode }, userActions.paste(event)) : null,
            cut: (event) =>
                userActions.cut ? eventWithStatistics({ type: EventTrackingType.KeyboardCutNode }, userActions.cut(event)) : null,
        }),
        [eventWithStatistics, keyHandlers, userActions],
    );
    useDocumentListeners(!disabled && eventHandlers);

    return null;
}
