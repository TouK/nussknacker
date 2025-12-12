import { css } from "@emotion/css";
import React, { useEffect, useRef } from "react";

import { useJsonTreeTheme } from "../../../../containers/theme/useJsonTreeTheme";
import { StaticHighlighted } from "../aggregate/groupBy/staticHighlighted";

export function useBoundedSelection() {
    const ref = useRef<HTMLElement>(null);
    const anchorRef = useRef(null);

    useEffect(() => {
        const selecting = css({
            userSelect: "none",
            "::selection": {
                backgroundColor: "transparent",
            },
        });

        function onSelectionChange() {
            const root = ref.current;
            if (!root) return;

            const sel = window.getSelection();
            if (!sel || sel.rangeCount === 0) {
                anchorRef.current = null;
                return;
            }

            const anchorNode = sel.anchorNode;
            if (!anchorNode) {
                anchorRef.current = null;
                return;
            }

            if (root.contains(anchorNode)) {
                anchorRef.current = { node: anchorNode, offset: sel.anchorOffset };
                document.body.classList.add(selecting);
            } else {
                anchorRef.current = null;
            }
        }

        function endSelection() {
            document.body.classList.remove(selecting);

            const root = ref.current;
            const anchor = anchorRef.current;
            if (!root || !anchor) return;

            const sel = window.getSelection();
            if (!sel || sel.rangeCount === 0) {
                anchorRef.current = null;
                return;
            }

            const focusNode = sel.focusNode;
            if (!focusNode) {
                anchorRef.current = null;
                return;
            }

            if (root.contains(focusNode)) {
                anchorRef.current = null;
                return;
            }

            const range = sel.getRangeAt(0);
            const newRange = document.createRange();

            const isForward = range.startContainer === anchor.node && range.startOffset === anchor.offset;

            try {
                if (isForward) {
                    newRange.setStart(anchor.node, anchor.offset);
                    newRange.setEnd(root, root.childNodes.length);
                } else {
                    newRange.setStart(root, 0);
                    newRange.setEnd(anchor.node, anchor.offset);
                }

                sel.removeAllRanges();
                sel.addRange(newRange);
            } catch (e) {
                // ignore
            }

            anchorRef.current = null;
        }

        document.addEventListener("selectionchange", onSelectionChange);
        document.addEventListener("pointerup", endSelection);

        return () => {
            document.removeEventListener("selectionchange", onSelectionChange);
            document.removeEventListener("pointerup", endSelection);
        };
    }, []);

    return ref;
}

export function RawDisplay({ data }: { data: Record<string, unknown> }) {
    const { treeStyle } = useJsonTreeTheme();
    const ref = useBoundedSelection();

    return (
        <StaticHighlighted
            ref={ref}
            sx={(theme) => ({
                ".ace_static_highlight": {
                    ...treeStyle,
                    fontSize: "1rem",
                    lineHeight: "1.5em",
                    userSelect: "text",
                },
                "::selection": {
                    backgroundColor: theme.palette.secondary.main,
                    color: theme.palette.getContrastText(theme.palette.secondary.main),
                },
            })}
        >
            {JSON.stringify(data, null, 2)}
        </StaticHighlighted>
    );
}
