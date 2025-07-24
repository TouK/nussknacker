import { useCallback, useEffect, useRef } from "react";

type EventType = MouseEvent | TouchEvent | PointerEvent | FocusEvent;

/**
 * Extended version of `useOutsideClickRef` from the `rooks` library.
 *
 * Handles clicks, touches, pointer events, and focus changes outside the given element.
 * Useful for closing modals, dropdowns, or other UI overlays when user interacts elsewhere.
 *
 * Includes support for:
 * - pointerdown / pointerup / click (general input)
 * - focusin (keyboard/programmatic focus)
 *
 * @param handler - Function to call when an outside interaction is detected.
 * @param when - Whether detection is active (defaults to true).
 * @returns A callback ref to attach to the target element.
 *
 * @see https://github.com/imbhargav5/rooks/blob/main/packages/use-outside-click-ref/src/index.ts
 */
function useOutsideInteractionRef(handler: (event: EventType) => void, when = true): [(node: HTMLElement | null) => void] {
    const savedHandler = useRef(handler);
    const nodeRef = useRef<HTMLElement | null>(null);

    useEffect(() => {
        savedHandler.current = handler;
    }, [handler]);

    const refCallback = useCallback((node: HTMLElement | null) => {
        nodeRef.current = node;
    }, []);

    useEffect(() => {
        if (!when) return;

        const isOutside = (event: Event) => {
            const node = nodeRef.current;
            return !node || !node.contains(event.target as Node);
        };

        const handleEvent = (event: EventType) => {
            if (isOutside(event)) {
                savedHandler.current(event);
            }
        };

        const eventTypes: (keyof DocumentEventMap)[] = ["pointerdown", "focusin"];

        eventTypes.forEach((type) => {
            document.addEventListener(type, handleEvent, true);
        });

        return () => {
            eventTypes.forEach((type) => {
                document.removeEventListener(type, handleEvent, true);
            });
        };
    }, [when]);

    return [refCallback];
}

export { useOutsideInteractionRef };
