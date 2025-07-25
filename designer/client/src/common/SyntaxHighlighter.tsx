import { useTheme } from "@mui/material";
import ace from "ace-builds/src-noconflict/ace";
import type { PropsWithChildren } from "react";
import { useEffect, useRef } from "react";
import React from "react";

import { getBorderColor } from "../containers/theme/helpers";

interface Props {
    language: string;
    customStyle?: React.CSSProperties;
    staticHighlightOptions?: Record<string, unknown>;
}

export const SyntaxHighlighter = ({ language, customStyle, children, staticHighlightOptions = {} }: PropsWithChildren<Props>) => {
    const containerRef = useRef<HTMLPreElement>(null);
    const theme = useTheme();

    useEffect(() => {
        if (containerRef.current) {
            // Set the code as text content first
            containerRef.current.textContent = children as string;

            // Highlight the code
            ace.require("ace/ext/static_highlight").highlight(
                containerRef.current,
                {
                    mode: `ace/mode/${language}`,
                    theme: "ace/theme/nussknacker",
                    startLineNumber: 1,
                    showGutter: true,
                    ...staticHighlightOptions,
                },
                function (result) {
                    console.log("Code highlighted successfully", result);
                },
            );
        }
    }, [children, language, staticHighlightOptions]);

    return <pre ref={containerRef} style={{ margin: 0, border: `1px solid ${getBorderColor(theme)}`, ...customStyle }} />;
};
