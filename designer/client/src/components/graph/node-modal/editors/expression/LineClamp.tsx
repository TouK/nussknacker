import React, { forwardRef, useLayoutEffect, useRef, useState } from "react";

interface LineClampProps extends React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
    lines: number;
    children: string;
}

export const LineClamp = forwardRef<HTMLDivElement, LineClampProps>(function LineClamp(
    { lines = 1, children = "", ...props },
    forwardedRef,
) {
    const ref = useRef<HTMLDivElement>();
    const [display, setDisplay] = useState(children);

    useLayoutEffect(() => {
        const el = ref.current;
        if (!el) return;
        el.style.overflowY = "hidden";

        const original = children;
        let low = 0;
        let high = original?.length;
        let result = original;

        const fits = (text) => {
            el.textContent = text;
            return Math.floor(el.scrollHeight) <= Math.ceil(lines * parseFloat(getComputedStyle(el).lineHeight));
        };

        if (fits(original)) return;

        while (low <= high) {
            const mid = Math.floor((low + high) / 2);
            const test = original.slice(0, mid) + "…";
            if (fits(test)) {
                result = test;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        setDisplay(result);
    }, [children, lines]);

    return (
        <div ref={forwardedRef} {...props}>
            <div ref={ref}>{display}</div>
        </div>
    );
});
