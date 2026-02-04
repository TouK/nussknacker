import type { MutableRefObject } from "react";
import { useEffect, useMemo } from "react";
import type ReactAce from "react-ace/lib/ace";
import type { IAceEditorProps } from "react-ace/lib/ace";
import type { IMarker } from "react-ace/lib/types";
import type { Annotation } from "react-ace/types";
import { v4 as uuid4 } from "uuid";

import { useUserSettings } from "../../../../../common/useUserSettings";
import type { FieldError } from "../Validators";

type ErrorCoords = {
    start: { row: number; column: number };
    end?: { row: number; column: number };
};

function extractLineAndColumnFromMessage(message: string): { line: number; column: number } | null {
    let match;

    // pattern: "... [character 16 line 37]"
    match = message.match(/\[\s*character\s+(\d+)\s*line\s+(\d+)\s*]/i);
    if (match) {
        const line = parseInt(match[2], 10);
        const column = parseInt(match[1], 10);
        return { line, column };
    }

    // pattern: "... (line 37, column 15)"
    match = message.match(/\(line\s+(\d+),\s*column\s+(\d+)\)/i);
    if (match) {
        const line = parseInt(match[1], 10);
        const column = parseInt(match[2], 10);
        return { line, column };
    }

    return null;
}

function extractErrorRange(error: FieldError): ErrorCoords | null {
    if (error.details?.type === "CoordinatesBasedTextRange") {
        return error.details;
    }

    const lineAndColumn = extractLineAndColumnFromMessage(error.message);
    if (lineAndColumn) {
        return { start: { row: lineAndColumn.line - 1, column: lineAndColumn.column - 1 } };
    }

    return null;
}

export function useAceEditorRangeMessages(fieldErrors: FieldError[], showLines?: boolean) {
    const [showRangeMessages] = useUserSettings("editor.showRangeMessages");

    const rangeErrors = useMemo(
        () =>
            fieldErrors
                .map((error) => {
                    const range = extractErrorRange(error);
                    return range ? { range, error } : null;
                })
                .filter(Boolean),
        [fieldErrors],
    );

    const annotations: Annotation[] = useMemo(() => {
        if (!showRangeMessages) {
            return [];
        }

        return rangeErrors.map(({ error, range }) => ({
            uuid: uuid4(),
            row: range.start.row,
            column: range.start.column,
            type: "error",
            text: error.message,
        }));
    }, [rangeErrors, showRangeMessages]);

    const markers: IMarker[] = useMemo(() => {
        return rangeErrors.map(({ range }) => ({
            uuid: uuid4(),
            startRow: range.start.row,
            startCol: range.start.column,
            endRow: range.end ? range.end.row : range.start.row,
            endCol: range.end ? range.end.column : range.start.column + 1,
            className: "ace-error-marker",
            type: "text" as const,
            inFront: false,
        }));
    }, [rangeErrors]);

    const hasRangeText = showLines && annotations.length > 0 && markers.length > 0;

    return { annotations, markers, hasRangeText };
}

/**
 * Manually sets annotations on the Ace editor session.
 * This is needed because we disable the Ace worker (useWorker: false) to avoid
 * MIME type errors when loading worker-json.js. Without the worker, annotations
 * must be set programmatically instead of being handled automatically by Ace.
 */
export const useSetAceEditorAnnotations = (editorRef: MutableRefObject<ReactAce>, annotations: IAceEditorProps["annotations"]) => {
    useEffect(() => {
        if (editorRef.current && annotations?.length > 0) {
            editorRef.current.editor.getSession().setAnnotations(annotations);
        }
    }, [annotations, editorRef]);
};
