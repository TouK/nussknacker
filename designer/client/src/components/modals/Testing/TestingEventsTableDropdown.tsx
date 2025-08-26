import React, { useCallback, useEffect, useRef } from "react";

interface Props {
    value: string;
    options: string[];
    onValueChange: (val: string) => void;
    onCommit: () => void;
    onCancel: () => void;
    style?: React.CSSProperties;
    autoOpen?: boolean; // new optional flag
}

export const TestingEventsTableDropdown: React.FC<Props> = ({
    value,
    options,
    onValueChange,
    onCommit,
    onCancel,
    style,
    autoOpen = true,
}) => {
    const selectRef = useRef<HTMLSelectElement>(null);
    const openedRef = useRef(false);

    useEffect(() => {
        const el = selectRef.current;
        if (!el) return;
        el.focus();
        if (!autoOpen || openedRef.current) return;
        openedRef.current = true;
        const tryOpen = () => {
            try {
                // Experimental API in some browsers
                (el as any).showPicker?.();
            } catch {
                /* ignore */
            }
            // Fallback: synthesize user interaction events
            try {
                const down = new MouseEvent("mousedown", { bubbles: true, cancelable: true, view: window });
                el.dispatchEvent(down);
                const up = new MouseEvent("mouseup", { bubbles: true, cancelable: true, view: window });
                el.dispatchEvent(up);
                const click = new MouseEvent("click", { bubbles: true, cancelable: true, view: window });
                el.dispatchEvent(click);
            } catch {
                /* ignore */
            }
        };
        // Delay to ensure element is attached and overlay positioned
        const id = window.setTimeout(tryOpen, 0);
        return () => window.clearTimeout(id);
    }, [autoOpen]);

    const handleKey = useCallback(
        (e: React.KeyboardEvent) => {
            if (e.key === "Enter" || e.key === "Tab") {
                e.preventDefault();
                onCommit();
            } else if (e.key === "Escape") {
                e.preventDefault();
                onCancel();
            }
        },
        [onCommit, onCancel],
    );

    return (
        <select
            ref={selectRef}
            autoFocus
            value={value}
            onChange={(e) => onValueChange(e.target.value)}
            onBlur={onCommit}
            onKeyDown={handleKey}
            style={{
                width: "100%",
                height: "100%",
                border: "none",
                outline: "none",
                background: "transparent",
                font: "inherit",
                padding: 0,
                margin: 0,
                ...style,
            }}
        >
            <option value="" />
            {options.map((o) => (
                <option key={o} value={o}>
                    {o}
                </option>
            ))}
        </select>
    );
};

export default TestingEventsTableDropdown;
