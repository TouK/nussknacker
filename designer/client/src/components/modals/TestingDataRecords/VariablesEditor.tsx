import React, { useCallback, useEffect, useState } from "react";

import type { NodeValidationError } from "../../../types/validation";
import { JsonEditor } from "../../graph/node-modal/editors/expression/JsonEditor";
import { EditorType, ExpressionLang } from "../../graph/node-modal/editors/expression/types";
import type { VariablesCell } from "./CellContent";
import type { TestingDataRecords } from "./types";

interface VariablesEditorProps {
    value: VariablesCell;
    onChange: (cell: VariablesCell) => void;
    onValidate?: (row: TestingDataRecords) => Promise<{ data: { validationErrors: NodeValidationError[] } }>;
}

export const VariablesEditor = ({ value, onChange, onValidate }: VariablesEditorProps) => {
    const [validationErrors, setValidationErrors] = useState<NodeValidationError[]>([]);

    const handleValidateData = useCallback(
        (row: TestingDataRecords) => {
            onValidate?.(row).then(({ data }) => setValidationErrors(data.validationErrors));
        },
        [onValidate],
    );

    useEffect(() => {
        handleValidateData({ sourceId: value.data.sourceId, variables: value.data.value });
    }, [handleValidateData, value]);

    return (
        <JsonEditor
            fieldName={"Input variables"}
            expressionObj={{ expression: value.data.value, language: ExpressionLang.JSON }}
            onValueChange={({ expression }) => {
                onChange({ ...value, copyData: expression, data: { ...value.data, value: expression } });
                handleValidateData({ sourceId: value.data.sourceId, variables: expression });
            }}
            className={""}
            fieldErrors={validationErrors}
            showValidation
            editorConfig={{
                type: EditorType.JSON_PARAMETER_EDITOR,
            }}
        />
    );
};
