import React, { useCallback, useEffect, useState } from "react";

import HttpService from "../../../http/HttpService/instance";
import { getProcessName, getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeValidationError } from "../../../types/validation";
import { JsonEditor } from "../../graph/node-modal/editors/expression/JsonEditor";
import { EditorType, ExpressionLang } from "../../graph/node-modal/editors/expression/types";
import type { VariablesCell } from "./CellContent";
import type { TestingDataRecords } from "./types";

interface VariablesEditorProps {
    value: VariablesCell;
    onChange: (cell: VariablesCell) => void;
}
export const VariablesEditor = ({ value, onChange }: VariablesEditorProps) => {
    const [validationErrors, setValidationErrors] = useState<NodeValidationError[]>([]);
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const handleValidateData = useCallback(
        (row: TestingDataRecords) => {
            HttpService.validateTestDataWithDataRecords(scenarioName, scenarioGraph, row).then(({ data }) => {
                setValidationErrors(data.validationErrors);
            });
        },
        [scenarioGraph, scenarioName],
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
