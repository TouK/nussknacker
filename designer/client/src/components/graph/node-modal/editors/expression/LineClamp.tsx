import React, { forwardRef, useLayoutEffect, useRef, useState } from "react";

function cloneAndTruncate(sourceNode: Element, maxLength: number): Element {
    const clonedNode = sourceNode.cloneNode(false) as Element;
    let remainingLength = maxLength;

    function traverse(source: Node, dest: Node) {
        for (const child of Array.from(source.childNodes)) {
            if (remainingLength <= 0) return;

            if (child.nodeType === Node.TEXT_NODE) {
                const text = child.textContent;
                const newTextNode = child.cloneNode(false) as Text;
                if (text.length > remainingLength) {
                    newTextNode.textContent = text.slice(0, remainingLength) + "…";
                    remainingLength = 0;
                } else {
                    newTextNode.textContent = text;
                    remainingLength -= text.length;
                }
                dest.appendChild(newTextNode);
            } else if (child.nodeType === Node.ELEMENT_NODE) {
                const newElement = child.cloneNode(false) as Element;
                dest.appendChild(newElement);
                traverse(child, newElement);
            } else {
                dest.appendChild(child.cloneNode(true));
            }
        }
    }

    traverse(sourceNode, clonedNode);
    return clonedNode;
}

interface LineClampProps extends React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
    lines: number;
}

function getLineHeight(el: HTMLDivElement) {
    const { lineHeight, fontSize } = getComputedStyle(el);
    const lh = parseFloat(lineHeight);
    if (lh) {
        return lh;
    }
    return parseFloat(fontSize) * 1.2;
}

export const LineClamp = forwardRef<HTMLDivElement, LineClampProps>(function LineClamp(
    { lines = 1, children /* supports basic markup, relays on lineHeight */, ...props },
    forwardedRef,
) {
    const ref = useRef<HTMLDivElement>();
    const [content, setContent] = useState<React.ReactNode>(children);

    useLayoutEffect(() => {
        setContent(children);
    }, [children]);

    useLayoutEffect(() => {
        if (content !== children) {
            return;
        }

        const el = ref.current;
        if (!el || (!el.firstElementChild && !el.textContent)) return;

        const measureEl = el.cloneNode(true) as HTMLElement;
        measureEl.style.position = "absolute";
        measureEl.style.visibility = "hidden";
        measureEl.style.height = "auto";
        measureEl.style.width = `${el.offsetWidth}px`;
        measureEl.style.overflowY = "hidden";

        el.parentElement.appendChild(measureEl);

        const lineHeight = getLineHeight(el);
        const fits = (text) => {
            measureEl.textContent = text;
            return Math.floor(measureEl.scrollHeight) <= Math.ceil(lines * lineHeight);
        };

        const originalText = measureEl.textContent;

        if (fits(originalText)) {
            el.parentElement.removeChild(measureEl);
            return;
        }

        let low = 0;
        let high = originalText.length;
        let maxLength = 0;

        while (low <= high) {
            const mid = Math.floor((low + high) / 2);
            if (fits(originalText.slice(0, mid) + "…")) {
                maxLength = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        el.parentElement.removeChild(measureEl);

        const truncatedElement = cloneAndTruncate(el, maxLength);
        setContent(<div dangerouslySetInnerHTML={{ __html: truncatedElement.innerHTML }} />);
    }, [children, content, lines]);

    return (
        <div ref={forwardedRef} {...props}>
            <div ref={ref}>{content}</div>
        </div>
    );
});
