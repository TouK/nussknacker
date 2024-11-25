import { useCallback, useMemo } from "react";
import { useSelectionActions } from "../components/graph/SelectionContextProvider";
import { EventTrackingSelector, EventTrackingType, TrackEventParams, useEventTracking } from "./event-tracking";
import { useDocumentListeners } from "./useDocumentListeners";

export const isInputTarget = (target: EventTarget): boolean => ["INPUT", "SELECT", "TEXTAREA"].includes(target?.["tagName"]);
export const isInputEvent = (event: Event): boolean => isInputTarget(event?.target);

type KeyboardShortcutsMap = Record<string, (event: KeyboardEvent) => void>;

export function BindKeyboardShortcuts({ disabled }: { disabled?: boolean }): JSX.Element {
    const userActions = useSelectionActions();
    const { trackEvent } = useEventTracking();

    const eventWithStatistics = useCallback(
        ({ selector }: Omit<TrackEventParams, "event">, event: unknown) => {
            trackEvent({ selector, event: EventTrackingType.Keyboard });
            return event;
        },
        [trackEvent],
    );

    const keyHandlers: KeyboardShortcutsMap = useMemo(
        () => ({
            A: (e) => {
                if (e.ctrlKey || e.metaKey) {
                    eventWithStatistics({ selector: EventTrackingSelector.SelectAllNodes }, userActions.selectAll(e));
                    e.preventDefault();
                }
            },
            Z: (e) =>
                (e.ctrlKey || e.metaKey) && e.shiftKey
                    ? eventWithStatistics({ selector: EventTrackingSelector.RedoScenarioChanges }, userActions.redo(e))
                    : eventWithStatistics({ selector: EventTrackingSelector.UndoScenarioChanges }, userActions.undo(e)),
            DELETE: (e) => eventWithStatistics({ selector: EventTrackingSelector.DeleteNodes }, userActions.delete(e)),
            BACKSPACE: (e) => eventWithStatistics({ selector: EventTrackingSelector.DeleteNodes }, userActions.delete(e)),
            ESCAPE: (e) => eventWithStatistics({ selector: EventTrackingSelector.DeselectAllNodes }, userActions.deselectAll(e)),
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
            copy: (event) => {
                if (isInputEvent(event)) return;
                userActions.copy ? eventWithStatistics({ selector: EventTrackingSelector.CopyNode }, userActions.copy(event)) : null;
            },
            paste: (event) => {
                userActions.paste ? eventWithStatistics({ selector: EventTrackingSelector.PasteNode }, userActions.paste(event)) : null;
            },
            cut: (event) => {
                if (isInputEvent(event)) return;
                userActions.cut ? eventWithStatistics({ selector: EventTrackingSelector.CutNode }, userActions.cut(event)) : null;
            },
        }),
        [eventWithStatistics, keyHandlers, userActions],
    );
    useDocumentListeners(!disabled && eventHandlers);

    return null;
}
