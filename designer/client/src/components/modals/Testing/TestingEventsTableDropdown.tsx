import React, { useCallback, useEffect, useRef } from "react";

interface Props {
    value: string;
    options: string[];
    onValueChange: (val: string) => void;
    onCommit: () => void;
    onCancel: () => void;
    style?: React.CSSProperties;
}

export const TestingEventsTableDropdown: React.FC<Props> = ({ value, options, onValueChange, onCommit, onCancel, style }) => {
    const selectRef = useRef<HTMLSelectElement>(null);

    useEffect(() => {
        selectRef.current?.focus();
    }, []);

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
