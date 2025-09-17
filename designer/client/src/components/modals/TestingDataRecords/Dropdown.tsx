import { List, ListItemButton, ListItemText, styled } from "@mui/material";
import i18next from "i18next";
import React, { useCallback, useEffect, useRef } from "react";

interface Props {
    value: string;
    options: string[];
    onValueChange: (val: string) => void;
    onCommit: () => void;
    onCancel: () => void;
    style?: React.CSSProperties;
    autoOpen?: boolean;
    commitOnClick?: boolean; // new flag to close editor immediately when user clicks
}

const StyledList = styled(List)(() => ({
    width: "100%",
    height: "100%",
    border: "none",
    outline: "none",
    background: "transparent",
    font: "inherit",
    padding: 0,
    margin: 0,
    overflowY: "auto",
}));

export const Dropdown: React.FC<Props> = ({ value, options, onValueChange, onCommit, onCancel, commitOnClick = false }) => {
    const listRef = useRef<HTMLUListElement>(null);
    const itemRefs = useRef<Record<string, HTMLDivElement | null>>({});

    useEffect(() => {
        listRef.current?.focus();
    }, []);

    // Ensure active item stays visible when value changes via keyboard
    useEffect(() => {
        const node = itemRefs.current[value];
        if (node && listRef.current) {
            const parent = listRef.current;
            const parentRect = parent.getBoundingClientRect();
            const nodeRect = node.getBoundingClientRect();
            if (nodeRect.top < parentRect.top) {
                parent.scrollTop -= parentRect.top - nodeRect.top;
            } else if (nodeRect.bottom > parentRect.bottom) {
                parent.scrollTop += nodeRect.bottom - parentRect.bottom;
            }
        }
    }, [value]);

    const moveSelection = useCallback(
        (delta: number) => {
            if (!options.length) return;
            const currentIndex = Math.max(0, options.indexOf(value));
            let nextIndex = currentIndex + delta;
            if (nextIndex < 0) nextIndex = 0;
            if (nextIndex >= options.length) nextIndex = options.length - 1;
            if (nextIndex !== currentIndex) onValueChange(options[nextIndex]);
        },
        [options, value, onValueChange],
    );

    const handleKey = useCallback(
        (e: React.KeyboardEvent<HTMLUListElement>) => {
            if (e.key === "Enter" || e.key === "Tab") {
                e.preventDefault();
                onCommit();
            } else if (e.key === "Escape") {
                e.preventDefault();
                onCancel();
            } else if (e.key === "ArrowDown") {
                e.preventDefault();
                moveSelection(1);
            } else if (e.key === "ArrowUp") {
                e.preventDefault();
                moveSelection(-1);
            } else if (e.key.length === 1 && /\S/.test(e.key)) {
                const lower = e.key.toLowerCase();
                const currentIndex = options.indexOf(value);
                const ordered = options
                    .map((opt, i) => ({ opt, i }))
                    .slice(currentIndex + 1)
                    .concat(options.map((opt, i) => ({ opt, i })).slice(0, currentIndex + 1));
                const found = ordered.find(({ opt }) => opt.toLowerCase().startsWith(lower));
                if (found) onValueChange(found.opt);
            }
        },
        [onCommit, onCancel, moveSelection, options, value, onValueChange],
    );

    return (
        <StyledList
            ref={listRef}
            tabIndex={0}
            role="listbox"
            aria-activedescendant={options.length ? `dropdown-option-${options.indexOf(value)}` : undefined}
            onKeyDown={handleKey}
            onBlur={onCommit}
            dense
            disablePadding
        >
            {options.map((o, i) => (
                <ListItemButton
                    key={o}
                    id={`dropdown-option-${i}`}
                    role="option"
                    aria-selected={o === value}
                    selected={o === value}
                    data-value={o}
                    ref={(el) => (itemRefs.current[o] = el)}
                    onMouseDown={(e) => {
                        e.preventDefault();
                        if (o !== value) onValueChange(o);
                    }}
                    onClick={() => {
                        if (o !== value) onValueChange(o);
                        if (commitOnClick) {
                            // Ensure commit after value change
                            onCommit();
                        }
                    }}
                >
                    <ListItemText primaryTypographyProps={{ noWrap: true }} primary={o} />
                </ListItemButton>
            ))}
            {!options.length && (
                <ListItemButton disabled sx={{ py: 0.25, px: 0.5 }}>
                    <ListItemText
                        primaryTypographyProps={{ fontStyle: "italic", noWrap: true, sx: { opacity: 0.7 } }}
                        primary={i18next.t("testingDataRecords.dropdown.noOptions", "No options")}
                    />
                </ListItemButton>
            )}
        </StyledList>
    );
};

export default Dropdown;
