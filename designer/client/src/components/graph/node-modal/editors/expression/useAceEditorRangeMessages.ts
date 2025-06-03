import { useMemo } from "react";
import type { IMarker } from "react-ace/lib/types";
import type { Annotation } from "react-ace/types";

import type { FieldError } from "../Validators";

export function useAceEditorRangeMessages(fieldErrors: FieldError[]) {
    const annotations: Annotation[] = useMemo(() => {
        return fieldErrors
            .map(
                (error) =>
                    error?.details?.type === "CoordinatesBasedTextRange" && {
                        row: error.details.start.row,
                        column: error.details.start.column,
                        type: "error",
                        text: error.message,
                    },
            )
            .filter(Boolean);
    }, [fieldErrors]);

    const markers: IMarker[] = useMemo(() => {
        return fieldErrors
            .map(
                (error): IMarker =>
                    error?.details?.type === "CoordinatesBasedTextRange" &&
                    error.details && {
                        startRow: error.details.start.row,
                        startCol: error.details.start.column,
                        endRow: error.details.end.row,
                        endCol: error.details.end.column,
                        className: "ace-error-marker",
                        type: "text",
                        inFront: false,
                    },
            )
            .filter(Boolean);
    }, [fieldErrors]);

    const hasRangeText = annotations.length > 0 && markers.length > 0;
    return { annotations, markers, hasRangeText };
}
