import staticHighlight from "ace-builds/src-noconflict/ext-static_highlight";
import React, { PropsWithChildren, useEffect, useRef } from "react";
import { EditorMode } from "../../editors/expression/types";

export const HighlightedSpel = ({ children }: PropsWithChildren<unknown>) => {
    const ref = useRef<HTMLSpanElement>(null);

    useEffect(() => {
        if (ref.current && children) {
            staticHighlight(ref.current, {
                mode: `ace/mode/${EditorMode.SpEL}`,
                theme: "ace/theme/nussknacker",
                trim: true,
            });
        }
    }, [children]);

    return <span ref={ref}>{children}</span>;
};
