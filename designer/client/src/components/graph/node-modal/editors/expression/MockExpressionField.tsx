import React, { useCallback } from "react";

import { setTestCaseMock } from "../../../../../actions/nk/testCasesActions";
import { getTestCaseMockForNode } from "../../../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../../../store/storeHelpers";
import type { UIParameter } from "../../../../../types/definition";
import type { NodeType } from "../../../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../../../types/validation";
import { NodeTable } from "../../NodeDetailsContent/NodeTable";
import { useDiffMark } from "../../PathsToMark";
import { EditableEditor } from "../EditableEditor";
import type { OnValueChange } from "./Editor";
import type { ExpressionObj } from "./types";
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
    defaultValue: { expression: "", language: "jsonTemplate" },
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
    variableTypes: VariableTypes;
    setNodeDataAt: <T>(propToMutate: string, newValue: T, defaultValue?: T) => void;
    errors: NodeValidationError[];
    defaultValue: ExpressionObj;
    isValidating: boolean;
};

function MockExpressionField(props: Props): React.JSX.Element {
    const dispatch = useAppDispatch();

    const { editedNode, isEditMode, showValidation, showSwitch, variableTypes, errors, defaultValue, isValidating } = props;
    const mockExpression = useAppSelector((state) => getTestCaseMockForNode(state, editedNode.id));

    const editMock: OnValueChange = useCallback(
        (expression) => {
            dispatch(setTestCaseMock(editedNode.id, expression));
        },
        [dispatch, editedNode.id],
    );

    const [isMarked] = useDiffMark();
    const readOnly = !isEditMode;

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
                fieldErrors={errors}
                defaultValue={defaultValue}
                isValidating={isValidating}
            />
        </NodeTable>
    );
}

export default MockExpressionField;
