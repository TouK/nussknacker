import React, { useCallback, useMemo } from "react";

import { setTestCaseMock } from "../../../../../actions/nk/testCasesActions";
import type ProcessUtils from "../../../../../common/ProcessUtils";
import { getTestCaseMockForNode } from "../../../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../../../store/storeHelpers";
import type { UIParameter } from "../../../../../types/definition";
import type { NodeType } from "../../../../../types/node";
import type { NodeValidationError } from "../../../../../types/validation";
import { NodeTable } from "../../NodeDetailsContent/NodeTable";
import { useDiffMark } from "../../PathsToMark";
import { EditableEditor } from "../EditableEditor";
import { getValidationErrorsForField } from "../Validators";
import type { OnValueChange } from "./Editor";
import { EditorType } from "./types";

const MOCK_EXPRESSION_IN_NODE_NAME = "mockExpression";
const MOCK_EXPRESSION_PARAMETER_NAME = "$mockExpression";
const EXPRESSION_TEXT_PATH = `${MOCK_EXPRESSION_IN_NODE_NAME}.expression`;

const UnknownTypingResult = {
    params: [],
    type: "Unknown",
    display: "Unknown",
    refClazzName: "java.lang.Object",
};

// we construct artificial and hardcoded parameter definition to reuse EditableEditor for mockExpression field
export const MockExpressionParameter: UIParameter = {
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
    const dispatch = useAppDispatch();

    const { editedNode, isEditMode, showValidation, showSwitch, findAvailableVariables, errors } = props;
    const mockExpression = useAppSelector((state) => getTestCaseMockForNode(state, editedNode.id));

    const editMock: OnValueChange = useCallback(
        (expression) => {
            dispatch(setTestCaseMock(editedNode.id, expression));
        },
        [dispatch, editedNode.id],
    );

    const [isMarked] = useDiffMark();
    const readOnly = !isEditMode;

    const variableTypes = useMemo(() => findAvailableVariables(editedNode.id), [findAvailableVariables, editedNode.id]);

    return (
        <NodeTable sx={{ flex: 1, m: 0 }}>
            <EditableEditor
                editors={MockExpressionParameter.editors}
                paramType={MockExpressionParameter.typ}
                expressionObj={mockExpression.expression}
                isMarked={isMarked(EXPRESSION_TEXT_PATH)}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                variableTypes={variableTypes}
                onValueChange={editMock}
                fieldErrors={getValidationErrorsForField(errors, MOCK_EXPRESSION_PARAMETER_NAME)}
            />
        </NodeTable>
    );
}

export default MockExpressionField;
