import { get } from "lodash";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import type ProcessUtils from "../../../../../common/ProcessUtils";
import type { NodeType, NodeValidationError, UIParameter } from "../../../../../types";
import { FieldLabel } from "../../FieldLabel";
import { useDiffMark } from "../../PathsToMark";
import { useTestResults } from "../../TestResultsWrapper";
import ExpressionTestResults from "../../tests/ExpressionTestResults";
import EditableEditor from "../EditableEditor";
import { getValidationErrorsForField } from "../Validators";
import type { OnValueChange } from "./Editor";
import type { ExpressionObj } from "./types";
import { EditorType } from "./types";

const MOCKED_OUTPUT_IN_NODE_FIELD_NAME = "mockedOutput";
const MOCK_EXPRESSION_PARAMETER_NAME = "mockExpression";
const EXPRESSION_TEXT_PATH = `${MOCKED_OUTPUT_IN_NODE_FIELD_NAME}.${MOCK_EXPRESSION_PARAMETER_NAME}.expression`;

const UnknownTypingResult = {
    params: [],
    type: "Unknown",
    display: "Unknown",
    refClazzName: "java.lang.Object",
};

// we construct artificial and hardcoded parameter definition to reuse EditableEditor for mockExpression field
const MockExpressionParameter: UIParameter = {
    additionalVariables: {},
    branchParam: false,
    defaultValue: { expression: "", language: "spel" },
    editor: { type: EditorType.SPEL_PARAMETER_EDITOR },
    label: "",
    name: MOCK_EXPRESSION_PARAMETER_NAME,
    typ: UnknownTypingResult,
    variablesToHide: [],
};

type Props = {
    editedNode: NodeType;
    isEditMode: boolean;
    showValidation: boolean;
    showSwitch: boolean;
    findAvailableVariables: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    setNodeDataAt: <T>(propToMutate: string, newValue: T, defaultValue?: T) => void;
    errors: NodeValidationError[];
};

function MockedOutputField(props: Props): JSX.Element {
    const { editedNode, isEditMode, showValidation, showSwitch, findAvailableVariables, setNodeDataAt, errors } = props;
    const [mockExpression, setMockExpression] = useState(() => {
        return get(editedNode, MOCKED_OUTPUT_IN_NODE_FIELD_NAME)?.mockExpression || { expression: "", language: "spel" };
    });
    const [isMarked] = useDiffMark();
    const { t } = useTranslation();
    const readOnly = !isEditMode;

    const onValueChange: OnValueChange = useCallback(
        (value: ExpressionObj) => {
            setMockExpression(value);
            if (value.expression.length > 0) {
                setNodeDataAt(MOCKED_OUTPUT_IN_NODE_FIELD_NAME, { type: "SingleMockExpression", mockExpression: value });
            } else {
                setNodeDataAt(MOCKED_OUTPUT_IN_NODE_FIELD_NAME, null);
            }
        },
        [setNodeDataAt],
    );

    const renderMockExpressionParameterLabel = (): JSX.Element => {
        return <FieldLabel title={MOCK_EXPRESSION_PARAMETER_NAME} label={t("nodes.enricher.mockExpression", "Mocked Output Expression")} />;
    };

    const variableTypes = useMemo(() => findAvailableVariables(editedNode.id), [findAvailableVariables, editedNode.id]);
    const testResultsState = useTestResults();

    return (
        <ExpressionTestResults fieldName={MOCK_EXPRESSION_PARAMETER_NAME} resultsToShow={testResultsState.testResultsToShow}>
            <EditableEditor
                param={MockExpressionParameter}
                renderFieldLabel={renderMockExpressionParameterLabel}
                fieldLabel={"unused"}
                expressionObj={mockExpression}
                isMarked={isMarked(EXPRESSION_TEXT_PATH)}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                variableTypes={variableTypes}
                onValueChange={onValueChange}
                fieldErrors={getValidationErrorsForField(errors, MOCK_EXPRESSION_PARAMETER_NAME)}
            />
        </ExpressionTestResults>
    );
}

export default MockedOutputField;
