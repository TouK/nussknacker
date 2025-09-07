import React, { useCallback, useEffect, useRef } from "react";

interface Props {
    value: string;
    options: string[];
    onValueChange: (val: string) => void;
    onCommit: () => void;
    onCancel: () => void;
    style?: React.CSSProperties;
    autoOpen?: boolean;
}

const EMPTY_OPTION_VALUE = "";
const BASE_SELECT_STYLE: React.CSSProperties = {
    width: "100%",
    height: "100%",
    border: "none",
    outline: "none",
    background: "transparent",
    font: "inherit",
    padding: 0,
    margin: 0,
};

interface SelectWithPicker extends HTMLSelectElement {
    showPicker?: () => void;
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

    // Focus on mount only
    useEffect(() => {
        const el = selectRef.current;
        if (!el) return;
        el.focus();
    }, []);

    useEffect(() => {
        if (!autoOpen) return;
        const el = selectRef.current;
        if (!el || openedRef.current) return;
        openedRef.current = true;
        const openSelect = () => {
            try {
                (el as SelectWithPicker).showPicker?.();
            } catch (e) {
                void e;
            }
            try {
                const events: Array<"mousedown" | "mouseup" | "click"> = ["mousedown", "mouseup", "click"];
                for (const type of events) {
                    const ev = new MouseEvent(type, { bubbles: true, cancelable: true, view: window });
                    el.dispatchEvent(ev);
                }
            } catch (e) {
                void e;
            }
        };
        const id = window.setTimeout(openSelect, 0);
        return () => window.clearTimeout(id);
    }, [autoOpen]);

    const handleKey = useCallback(
        (e: React.KeyboardEvent<HTMLSelectElement>) => {
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
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => onValueChange(e.target.value)}
            onBlur={onCommit}
            onKeyDown={handleKey}
            style={{ ...BASE_SELECT_STYLE, ...style }}
        >
            <option value={EMPTY_OPTION_VALUE} />
            {options.map((o) => (
                <option key={o} value={o}>
                    {o}
                </option>
            ))}
        </select>
    );
};

export default TestingEventsTableDropdown;
