import { get } from "lodash";
import type { ReactNode } from "react";
import React, { useCallback } from "react";

import type { NodeResultsForContext } from "../../../../../common/TestResultUtils";
import type { UIParameter } from "../../../../../types/definition";
import type { NodeType } from "../../../../../types/node";
import type { VariableTypes } from "../../../../../types/validation";
import { useDiffMark } from "../../PathsToMark";
import ExpressionTestResults from "../../tests/ExpressionTestResults";
import EditableEditor from "../EditableEditor";
import type { FieldError } from "../Validators";
import type { OnValueChange } from "./Editor";
import type { ExpressionObj } from "./types";
import { EditorType } from "./types";

export type ExpressionFieldProps = {
    fieldName: string;
    fieldLabel: string;
    exprPath: string;
    isEditMode: boolean;
    editedNode: NodeType;
    showValidation: boolean;
    showSwitch: boolean;
    parameterDefinition: UIParameter;
    setNodeDataAt: <T>(propToMutate: string, newValue: T, defaultValue?: T) => void;
    testResultsToShow: NodeResultsForContext;
    variableTypes: VariableTypes;
    fieldErrors: FieldError[];
    endAdornment?: ReactNode;
};

function ExpressionField(props: ExpressionFieldProps): React.JSX.Element {
    const {
        fieldName,
        fieldLabel,
        exprPath,
        isEditMode,
        editedNode,
        showValidation,
        showSwitch,
        parameterDefinition,
        setNodeDataAt,
        testResultsToShow,
        variableTypes,
        fieldErrors,
        endAdornment,
    } = props;
    const [isMarked] = useDiffMark();
    const readOnly = !isEditMode;
    const exprTextPath = `${exprPath}.expression`;
    const expressionObj = get(editedNode, exprPath);
    const editors = parameterDefinition?.editors || [];

    const onValueChange: OnValueChange = useCallback(
        (value: ExpressionObj) => {
            setNodeDataAt(exprPath, value);
        },
        [exprPath, setNodeDataAt],
    );

    if (
        editors.some(
            (editor) =>
                editor?.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR ||
                editor?.type === EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR,
        )
    ) {
        return (
            <EditableEditor
                fieldLabel={fieldLabel}
                editors={editors}
                paramType={parameterDefinition?.typ}
                defaultValue={parameterDefinition?.defaultValue}
                expressionObj={expressionObj}
                isMarked={isMarked(exprTextPath)}
                showSwitch={showSwitch}
                readOnly={readOnly}
                onValueChange={onValueChange}
                variableTypes={variableTypes}
                showValidation={showValidation}
                fieldErrors={fieldErrors}
                endAdornment={endAdornment}
            />
        );
    }

    return (
        <ExpressionTestResults fieldName={fieldName} resultsToShow={testResultsToShow}>
            <EditableEditor
                editors={editors}
                paramType={parameterDefinition?.typ}
                defaultValue={parameterDefinition?.defaultValue}
                fieldLabel={fieldLabel}
                expressionObj={expressionObj}
                isMarked={isMarked(exprTextPath)}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                variableTypes={variableTypes}
                onValueChange={onValueChange}
                fieldErrors={fieldErrors}
                endAdornment={endAdornment}
            />
        </ExpressionTestResults>
    );
}

export default ExpressionField;
