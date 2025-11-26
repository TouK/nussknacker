import { useMemo } from "react";
import type { IMarker } from "react-ace/lib/types";
import type { Annotation } from "react-ace/types";
import { v4 as uuid4 } from "uuid";

import { getUserSettings } from "../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../store/storeHelpers";
import type { FieldError } from "../Validators";

export function useAceEditorRangeMessages(fieldErrors: FieldError[], showLines?: boolean) {
    const userSettings = useAppSelector(getUserSettings);
    const showRangeMessages = userSettings["editor.showRangeMessages"];

    const annotations: Annotation[] = useMemo(() => {
        if (!showRangeMessages) {
            return [];
        }

        return fieldErrors
            .map(
                (error) =>
                    error?.details?.type === "CoordinatesBasedTextRange" && {
                        uuid: uuid4(), // Unique identifier for the annotation to fix issue, when annotations are not updated when, line is updated, but annotations object is the same
                        row: error.details.start.row,
                        column: error.details.start.column,
                        type: "error",
                        text: error.message,
                    },
            )
            .filter(Boolean);
    }, [fieldErrors, showRangeMessages]);

    const markers: IMarker[] = useMemo(() => {
        return fieldErrors
            .map(
                (error) =>
                    error?.details?.type === "CoordinatesBasedTextRange" &&
                    error.details && {
                        uuid: uuid4(), // Unique identifier for the marker to fix issue, when markers are not updated when, line is updated, but markers object is the same
                        startRow: error.details.start.row,
                        startCol: error.details.start.column,
                        endRow: error.details.end.row,
                        endCol: error.details.end.column,
                        className: "ace-error-marker",
                        type: "text" as const,
                        inFront: false,
                    },
            )
            .filter(Boolean);
    }, [fieldErrors]);

    const hasRangeText = showLines && annotations.length > 0 && markers.length > 0;

    return { annotations, markers, hasRangeText };
}
