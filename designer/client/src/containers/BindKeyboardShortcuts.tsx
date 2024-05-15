import { useCallback, useMemo } from "react";
import { useSelectionActions } from "../components/graph/SelectionContextProvider";
import { useDocumentListeners } from "./useDocumentListeners";
import { TrackEventParams, useEventTracking } from "./event-tracking";

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
                    eventWithStatistics({ type: "KEYBOARD_SELECT_ALL_NODES" }, userActions.selectAll(e));
                    e.preventDefault();
                }
            },
            Z: (e) =>
                (e.ctrlKey || e.metaKey) && e.shiftKey
                    ? eventWithStatistics({ type: "KEYBOARD_REDO_SCENARIO_CHANGES" }, userActions.redo(e))
                    : eventWithStatistics({ type: "KEYBOARD_UNDO_SCENARIO_CHANGES" }, userActions.undo(e)),
            DELETE: (e) => eventWithStatistics({ type: "KEYBOARD_DELETE_NODES" }, userActions.delete(e)),
            BACKSPACE: (e) => eventWithStatistics({ type: "KEYBOARD_DELETE_NODES" }, userActions.delete(e)),
            ESCAPE: (e) => eventWithStatistics({ type: "KEYBOARD_DESELECT_ALL_NODES" }, userActions.deselectAll(e)),
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
            copy: (event) => (userActions.copy ? eventWithStatistics({ type: "KEYBOARD_COPY_NODE" }, userActions.copy(event)) : null),
            paste: (event) => (userActions.paste ? eventWithStatistics({ type: "KEYBOARD_PASTE_NODE" }, userActions.paste(event)) : null),
            cut: (event) => (userActions.cut ? eventWithStatistics({ type: "KEYBOARD_CUT_NODE" }, userActions.cut(event)) : null),
        }),
        [eventWithStatistics, keyHandlers, userActions],
    );
    useDocumentListeners(!disabled && eventHandlers);

    return null;
}
