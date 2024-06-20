import React, { ReactNode, useCallback } from "react";
import { NodeType, UIParameter, VariableTypes } from "../../../../../types";
import { UnknownFunction } from "../../../../../types/common";
import ExpressionTestResults from "../../tests/ExpressionTestResults";
import EditableEditor from "../EditableEditor";
import { EditorType } from "./Editor";
import { NodeResultsForContext } from "../../../../../common/TestResultUtils";
import { useDiffMark } from "../../PathsToMark";
import { get } from "lodash";
import { FieldError } from "../Validators";

type Props = {
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
    renderFieldLabel: (paramName: string) => ReactNode;
    variableTypes: VariableTypes;
    fieldErrors: FieldError[];
};

function ExpressionField(props: Props): JSX.Element {
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
        renderFieldLabel,
        variableTypes,
        fieldErrors,
    } = props;
    const [isMarked] = useDiffMark();
    const readOnly = !isEditMode;
    const exprTextPath = `${exprPath}.expression`;
    const expressionObj = get(editedNode, exprPath);
    const editor = parameterDefinition?.editor || {};

    const onValueChange = useCallback((newValue) => setNodeDataAt(exprTextPath, newValue), [exprTextPath, setNodeDataAt]);

    if (editor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR) {
        return (
            <EditableEditor
                fieldLabel={fieldLabel}
                param={parameterDefinition}
                expressionObj={expressionObj}
                renderFieldLabel={renderFieldLabel}
                isMarked={isMarked(exprTextPath)}
                showSwitch={showSwitch}
                readOnly={readOnly}
                onValueChange={onValueChange}
                variableTypes={variableTypes}
                showValidation={showValidation}
                fieldErrors={fieldErrors}
            />
        );
    }

    return (
        <ExpressionTestResults fieldName={fieldName} resultsToShow={testResultsToShow}>
            <EditableEditor
                param={parameterDefinition}
                renderFieldLabel={renderFieldLabel}
                fieldLabel={fieldLabel}
                expressionObj={expressionObj}
                isMarked={isMarked(exprTextPath)}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                variableTypes={variableTypes}
                onValueChange={onValueChange}
                fieldErrors={fieldErrors}
            />
        </ExpressionTestResults>
    );
}

export default ExpressionField;
