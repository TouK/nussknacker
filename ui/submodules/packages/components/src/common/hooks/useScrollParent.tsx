import { RefObject, useRef, useState } from "react";
import { useMutationObserver } from "rooks";

function isSCrollDisabledByCss(element: Element) {
    if (!(element instanceof Element)) {
        return false;
    }
    const overflowY = getComputedStyle(element)?.overflowY;
    return overflowY?.includes("hidden") || overflowY?.includes("visible");
}

function isScrollable(element: Element): boolean {
    const alreadyScrolled = element.scrollTop > 0;
    const contentOverflows = element.scrollHeight > element.clientHeight;
    return (alreadyScrolled || contentOverflows) && !isSCrollDisabledByCss(element);
}

export function getScrollParent(element: Element): Element | null {
    if (!element) {
        return;
    }

    if (isScrollable(element)) {
        return element;
    }

    return getScrollParent(element.parentNode as Element);
}

export function useScrollParent<T extends HTMLElement = HTMLDivElement>(): { scrollParent: Element; ref: RefObject<T> } {
    const [scrollParent, setScrollParent] = useState<Element>(document.body);
    const ref = useRef<T>();

    useMutationObserver(ref, () => {
        if (!scrollParent || scrollParent === document.body) {
            setScrollParent(getScrollParent(ref.current?.parentNode as Element));
        }
    });

    return { scrollParent, ref };
}
