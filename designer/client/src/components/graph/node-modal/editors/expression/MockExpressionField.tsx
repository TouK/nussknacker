import { get } from "lodash";
import React, { useCallback, useMemo, useState } from "react";

import type ProcessUtils from "../../../../../common/ProcessUtils";
import type { UIParameter } from "../../../../../types/definition";
import type { NodeType } from "../../../../../types/node";
import type { NodeValidationError } from "../../../../../types/validation";
import { NodeTable } from "../../NodeDetailsContent/NodeTable";
import { useDiffMark } from "../../PathsToMark";
import { EditableEditor } from "../EditableEditor";
import { getValidationErrorsForField } from "../Validators";
import type { OnValueChange } from "./Editor";
import type { ExpressionObj } from "./types";
import { EditorType, ExpressionLang } from "./types";

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

    const variableTypes = useMemo(() => findAvailableVariables(editedNode.id), [findAvailableVariables, editedNode.id]);

    return (
        <NodeTable sx={{ flex: 1, m: 0 }}>
            <EditableEditor
                editors={MockExpressionParameter.editors}
                paramType={MockExpressionParameter.typ}
                expressionObj={mockExpression}
                isMarked={isMarked(EXPRESSION_TEXT_PATH)}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                variableTypes={variableTypes}
                onValueChange={onValueChange}
                fieldErrors={getValidationErrorsForField(errors, MOCK_EXPRESSION_PARAMETER_NAME)}
            />
        </NodeTable>
    );
}

export default MockExpressionField;
