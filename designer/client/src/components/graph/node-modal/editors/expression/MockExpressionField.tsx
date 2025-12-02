import { get } from "lodash";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import type ProcessUtils from "../../../../../common/ProcessUtils";
import type { UIParameter } from "../../../../../types/definition";
import type { NodeType } from "../../../../../types/node";
import type { NodeValidationError } from "../../../../../types/validation";
import { FieldLabel } from "../../FieldLabel";
import { useDiffMark } from "../../PathsToMark";
import { useTestResults } from "../../TestResultsWrapper";
import ExpressionTestResults from "../../tests/ExpressionTestResults";
import EditableEditor from "../EditableEditor";
import { FieldLabelProvider } from "../RenderFieldLabel";
import { getValidationErrorsForField } from "../Validators";
import type { OnValueChange } from "./Editor";
import type { ExpressionObj } from "./types";
import { EditorType, ExpressionLang } from "./types";

const MOCK_EXPRESSION_IN_NODE_NAME = "mockExpression";
const MOCK_EXPRESSION_PARAMETER_NAME = "$mockExpression";
const EXPRESSION_TEXT_PATH = `${MOCK_EXPRESSION_IN_NODE_NAME}.expression`;

const MOCK_EXPRESSION_HINT_TEXT =
    "If you provide this expression, the real service won't be invoked during tests. Instead, the result of the evaluation will be used.";

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
    editors: [{ type: EditorType.JSON_TEMPLATE_PARAMETER_EDITOR }, { type: EditorType.SPEL_PARAMETER_EDITOR }],
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

function MockExpressionField(props: Props): React.JSX.Element {
    const { editedNode, isEditMode, showValidation, showSwitch, findAvailableVariables, setNodeDataAt, errors } = props;
    const [mockExpression, setMockExpression] = useState(() => {
        return get(editedNode, MOCK_EXPRESSION_IN_NODE_NAME) || { expression: "", language: ExpressionLang.JsonTemplate };
    });
    const [isMarked] = useDiffMark();
    const { t } = useTranslation();
    const readOnly = !isEditMode;

    const onValueChange: OnValueChange = useCallback(
        (value: ExpressionObj) => {
            setMockExpression(value);
            if (value.expression.length > 0) {
                setNodeDataAt(MOCK_EXPRESSION_IN_NODE_NAME, value);
            } else {
                setNodeDataAt(MOCK_EXPRESSION_IN_NODE_NAME, null);
            }
        },
        [setNodeDataAt],
    );

    const renderMockExpressionParameterLabel = (): React.JSX.Element => {
        return <FieldLabel label={t("nodes.enricher.mockExpression", "Mock")} hintText={MOCK_EXPRESSION_HINT_TEXT} />;
    };

    const variableTypes = useMemo(() => findAvailableVariables(editedNode.id), [findAvailableVariables, editedNode.id]);
    const testResultsState = useTestResults();

    return (
        <ExpressionTestResults fieldName={MOCK_EXPRESSION_PARAMETER_NAME} resultsToShow={testResultsState.testResultsToShow}>
            <FieldLabelProvider value={renderMockExpressionParameterLabel}>
                <EditableEditor
                    editors={MockExpressionParameter.editors}
                    paramType={MockExpressionParameter.typ}
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
            </FieldLabelProvider>
        </ExpressionTestResults>
    );
}

export default MockExpressionField;
