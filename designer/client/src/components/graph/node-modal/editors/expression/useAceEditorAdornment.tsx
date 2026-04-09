import React, { useEffect, useMemo, useRef, useState } from "react";
import type ReactAce from "react-ace/lib/ace";

import { FullscreenButton } from "./FullscreenButton";
import { ResetToDefault } from "./ResetToDefaultButton";
import type { ExpressionObj } from "./types";
import { ExpressionLang } from "./types";

const COLLAPSED_MAX_LINES = 16;

type Params = {
    value: string;
    defaultValue?: ExpressionObj | string;
    readOnly?: boolean;
    onChange: (expression: string) => void;
};

type Result = {
    editorRef: React.RefObject<ReactAce>;
    maxLines: number;
    resetToDefaultButton: React.ReactNode;
    fullscreenButton: React.ReactNode;
};

export function useAceEditorAdornment({ value, defaultValue, readOnly, onChange }: Params): Result {
    const editorRef = useRef<ReactAce>(null);
    const [hasScrollbar, setHasScrollbar] = useState(false);
    const [isExpanded, setIsExpanded] = useState(false);

    useEffect(() => {
        const editor = editorRef.current?.editor;
        if (!editor) return;
        const checkScrollbar = () => setHasScrollbar(editor.session.getLength() > COLLAPSED_MAX_LINES);
        editor.session.on("change", checkScrollbar);
        checkScrollbar();
        return () => editor.session.off("change", checkScrollbar);
    }, []);

    const resetToDefaultButton = useMemo(() => {
        if (!defaultValue || readOnly) return null;
        return (
            <ResetToDefault
                value={value}
                defaultValue={
                    // defaultValue can be a string in case of Properties
                    typeof defaultValue === "string" ? { expression: defaultValue, language: ExpressionLang.JSON } : defaultValue
                }
                handleChange={({ expression }) => onChange(expression)}
            />
        );
    }, [defaultValue, readOnly, value, onChange]);

    const fullscreenButton = useMemo(() => {
        if (!hasScrollbar && !isExpanded) return null;
        return <FullscreenButton isFullscreen={isExpanded} onToggle={() => setIsExpanded((v) => !v)} />;
    }, [hasScrollbar, isExpanded]);

    return {
        editorRef,
        maxLines: isExpanded ? 512 : COLLAPSED_MAX_LINES,
        resetToDefaultButton,
        fullscreenButton,
    };
}
