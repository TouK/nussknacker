import { useEffect, useRef } from "react";

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
 * @param ref - Ref object of the target element.
 * @param handler - Function to call when an outside interaction is detected.
 * @param when - Whether detection is active (defaults to true).
 *
 * @see https://github.com/imbhargav5/rooks/blob/main/packages/use-outside-click-ref/src/index.ts
 */
function useOutsideInteraction(ref: React.RefObject<HTMLElement | null>, handler: () => void, when = true): void {
    const savedHandler = useRef(handler);

    useEffect(() => {
        savedHandler.current = handler;
    }, [handler]);

    useEffect(() => {
        if (!when) return;

        const isOutside = (event: Event) => {
            const node = ref.current;
            return !node || !node.contains(event.target as Node);
        };

        const handleEvent = (event: EventType) => {
            if (isOutside(event)) {
                savedHandler.current();
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
    }, [ref, when]);
}

export { useOutsideInteraction };
