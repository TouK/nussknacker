import { styled } from "@mui/material";
import staticHighlight from "ace-builds/src-noconflict/ext-static_highlight";
import type { DetailedHTMLProps, HTMLAttributes } from "react";
import React, { forwardRef, useEffect, useRef } from "react";
import { useMergeRefs } from "rooks";

import { ExpressionLang } from "../../editors/expression/types";

interface StaticHighlightedProps extends DetailedHTMLProps<HTMLAttributes<HTMLSpanElement>, HTMLSpanElement> {
    lang?: ExpressionLang;
}

const StaticHighlightedSpan = forwardRef<HTMLSpanElement, StaticHighlightedProps>(function StaticHighlightedSpan(
    { children, lang = ExpressionLang.SpEL, ...props },
    forwardedRef,
) {
    const ref = useRef<HTMLSpanElement>(null);

    useEffect(() => {
        if (ref.current && children) {
            staticHighlight(ref.current, {
                mode: `ace/mode/${lang}`,
                theme: "ace/theme/nussknacker",
                trim: true,
            });
        }
    }, [children, lang]);

    const mergedRef = useMergeRefs(forwardedRef, ref);

    return (
        <span ref={mergedRef} {...props}>
            {children}
        </span>
    );
});

export const StaticHighlighted = styled(StaticHighlightedSpan)({
    display: "contents",
});
