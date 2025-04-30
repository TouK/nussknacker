import { get } from "lodash";
import type { ReactNode} from "react";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import type { NodeType, NodeValidationError, UIParameter } from "../../../../../types";
import { useDiffMark } from "../../PathsToMark";
import { useTestResults } from "../../TestResultsWrapper";
import ExpressionTestResults from "../../tests/ExpressionTestResults";
import EditableEditor from "../EditableEditor";
import { getValidationErrorsForField } from "../Validators";
import type { OnValueChange } from "./Editor";
import type { ExpressionObj } from "./types";
import { EditorType } from "./types";

const MOCK_EXPRESSION_FIELD_NAME = "mockExpression";

type Props = {
    editedNode: NodeType;
    isEditMode: boolean;
    showValidation: boolean;
    showSwitch: boolean;
    setNodeDataAt: <T>(propToMutate: string, newValue: T, defaultValue?: T) => void;
    renderFieldLabel: (paramName: string) => ReactNode;
    errors: NodeValidationError[];
};

function MockExpressionField(props: Props): JSX.Element {
    const { editedNode, isEditMode, showValidation, showSwitch, setNodeDataAt, renderFieldLabel, errors } = props;
    const [mockExpression, setMockExpression] = useState(() => {
        return get(editedNode, MOCK_EXPRESSION_FIELD_NAME) || { expression: "", language: "spel" };
    });
    const [isMarked] = useDiffMark();
    const readOnly = !isEditMode;
    const exprTextPath = `${MOCK_EXPRESSION_FIELD_NAME}.expression`;

    const onValueChange: OnValueChange = useCallback(
        (value: ExpressionObj) => {
            setMockExpression(value);
            if (value.expression.length > 0) {
                setNodeDataAt(MOCK_EXPRESSION_FIELD_NAME, value);
            } else {
                setNodeDataAt(MOCK_EXPRESSION_FIELD_NAME, null);
            }
        },
        [setNodeDataAt],
    );

    const { t } = useTranslation();
    const testResultsState = useTestResults();

    const unknownTypingResult = {
        params: [],
        type: "Unknown",
        display: "Unknown",
        refClazzName: "java.lang.Object",
    };

    const mockExpressionParameter: UIParameter = {
        additionalVariables: {},
        branchParam: false,
        defaultValue: { expression: "", language: undefined },
        editor: { type: EditorType.SPEL_PARAMETER_EDITOR },
        label: "",
        name: MOCK_EXPRESSION_FIELD_NAME,
        typ: unknownTypingResult,
        variablesToHide: undefined,
    };

    return (
        <ExpressionTestResults fieldName={MOCK_EXPRESSION_FIELD_NAME} resultsToShow={testResultsState.testResultsToShow}>
            <EditableEditor
                param={mockExpressionParameter}
                renderFieldLabel={renderFieldLabel}
                fieldLabel={t("nodes.enricher.mockExpression", "Mock expression")}
                expressionObj={mockExpression}
                isMarked={isMarked(exprTextPath)}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                variableTypes={{}}
                onValueChange={onValueChange}
                fieldErrors={getValidationErrorsForField(errors, MOCK_EXPRESSION_FIELD_NAME)} // todo: check if it works
            />
        </ExpressionTestResults>
    );
}

export default MockExpressionField;
