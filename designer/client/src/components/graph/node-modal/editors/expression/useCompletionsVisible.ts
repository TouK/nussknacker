import { useEffect, useState } from "react";
import type ReactAce from "react-ace/lib/ace";
import { usePreviousImmediate } from "rooks";

/**
 * Tracks whether the ACE autocomplete suggestion popup is currently visible.
 *
 * Use cases:
 *
 * 1. Detecting popup open
 *    The ACE completer popup appears after suggestions finish loading. We detect
 *    this via the isLoading true→false transition and check the completer state
 *    with a setTimeout(0) to let ACE finish rendering before reading it.
 *
 * 2. Detecting popup close via keyboard (Escape / arrow + Enter)
 *    While the popup is open, keyboardActivity and change events on the editor
 *    re-check completer state. Listeners are registered only while the popup is
 *    open and cleaned up when it closes to avoid unnecessary event overhead.
 *
 * 3. Detecting popup close via blur (click outside)
 *    The blur event on the editor triggers the same completer state check, and
 *    additionally snapshots the editor's current value so that a suggestion
 *    inserted by click is captured in internalValue before the flush.
 */
export function useCompletionsVisible({
    editorRef,
    isLoading,
    onInternalValueChange,
}: {
    editorRef: React.MutableRefObject<ReactAce | undefined>;
    isLoading?: boolean;
    onInternalValueChange: (value: string) => void;
}): boolean {
    const [completionsVisible, setCompletionsVisible] = useState(false);

    // Detect popup opening: completions arrive when isLoading transitions true→false
    const previousLoadingState = usePreviousImmediate(isLoading);
    useEffect(() => {
        if (previousLoadingState && !isLoading) {
            setTimeout(() => setCompletionsVisible(getCompletionsActivated(editorRef)), 0);
        }
    }, [editorRef, isLoading, previousLoadingState]);

    // Detect popup closing: listen to ACE events only while popup is open
    useEffect(() => {
        const editor = editorRef.current?.editor;
        if (!editor || !completionsVisible) return;

        const updateCompletionsVisible = (callback?: () => void) => {
            setTimeout(() => {
                setCompletionsVisible(getCompletionsActivated(editorRef));
                callback?.();
            }, 0);
        };
        const onBlur = () => updateCompletionsVisible(() => onInternalValueChange(editor.getValue()));
        const onChange = () => updateCompletionsVisible();
        const keyboardActivity = () => updateCompletionsVisible();

        editor.on("keyboardActivity" as any, keyboardActivity);
        editor.on("change", onChange);
        editor.on("blur", onBlur);
        return () => {
            editor.off("keyboardActivity" as any, keyboardActivity);
            editor.off("change", onChange);
            editor.off("blur", onBlur);
        };
    }, [completionsVisible, editorRef, onInternalValueChange]);

    return completionsVisible;
}

function getCompletionsActivated(editorRef: React.MutableRefObject<ReactAce | undefined>): boolean {
    const completer = editorRef.current?.editor.completer;
    if (!completer) return false;
    return Boolean(completer.activated && completer.getPopup?.()?.isOpen);
}
