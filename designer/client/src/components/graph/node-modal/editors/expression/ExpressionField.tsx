import { get } from "lodash";
import type { ReactNode } from "react";
import React, { useCallback, useMemo } from "react";

import type { NodeResultsForContext } from "../../../../../common/TestResultUtils";
import { useUserSettings } from "../../../../../common/useUserSettings";
import type { UIParameter } from "../../../../../types/definition";
import type { NodeType } from "../../../../../types/node";
import type { VariableTypes } from "../../../../../types/validation";
import { useDiffMark } from "../../PathsToMark";
import ExpressionTestResults from "../../tests/ExpressionTestResults";
import EditableEditor from "../EditableEditor";
import type { FieldError } from "../Validators";
import type { OnValueChange } from "./Editor";
import { FlinkSqlTemplateEditor } from "./FlinkSqlTemplateEditor";
import type { ExpressionObj } from "./types";
import { EditorType } from "./types";

const FLINK_SQL_PARAM = "flinkSqlQuery";

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
    outputVarErrors?: FieldError[];
    endAdornment?: ReactNode;
    inputAdornmentEnd?: ReactNode;
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
        outputVarErrors,
        endAdornment,
        inputAdornmentEnd,
    } = props;
    const [isMarked] = useDiffMark();
    const readOnly = !isEditMode;
    const exprTextPath = `${exprPath}.expression`;
    const expressionObj = useMemo(() => get(editedNode, exprPath), [editedNode, exprPath]);
    const editors = useMemo(() => parameterDefinition?.editors || [], [parameterDefinition?.editors]);
    const [showFlinkSqlTemplateEditor] = useUserSettings("node.showFlinkSqlTemplateEditor");

    const onValueChange: OnValueChange = useCallback(
        (value: ExpressionObj) => {
            setNodeDataAt(exprPath, value);
        },
        [exprPath, setNodeDataAt],
    );

    const isFlinkSqlQuery =
        showFlinkSqlTemplateEditor && fieldName === FLINK_SQL_PARAM && editors.some((e) => e.type === EditorType.SQL_PARAMETER_EDITOR);

    const sqlEditor = useMemo(
        () => (
            <EditableEditor
                defaultValue={parameterDefinition?.defaultValue}
                editors={editors}
                endAdornment={endAdornment}
                inputAdornmentEnd={inputAdornmentEnd}
                expressionObj={expressionObj}
                fieldErrors={fieldErrors}
                fieldLabel={fieldLabel}
                isMarked={isMarked(exprTextPath)}
                onValueChange={onValueChange}
                paramType={parameterDefinition?.typ}
                readOnly={readOnly}
                showSwitch={showSwitch}
                showValidation={showValidation}
                variableTypes={variableTypes}
            />
        ),
        [
            editors,
            endAdornment,
            inputAdornmentEnd,
            exprTextPath,
            expressionObj,
            fieldErrors,
            fieldLabel,
            isMarked,
            onValueChange,
            parameterDefinition?.defaultValue,
            parameterDefinition?.typ,
            readOnly,
            showSwitch,
            showValidation,
            variableTypes,
        ],
    );

    const editor = useMemo(() => {
        if (isFlinkSqlQuery) {
            return (
                <FlinkSqlTemplateEditor
                    expressionObj={expressionObj}
                    onValueChange={onValueChange}
                    readOnly={readOnly}
                    variableTypes={variableTypes}
                    SqlEditorComponent={sqlEditor}
                    outputVar={editedNode.outputVar}
                    onOutputVarChange={(name) => setNodeDataAt("outputVar", name)}
                    fieldErrors={fieldErrors}
                    outputVarErrors={outputVarErrors}
                    showValidation={showValidation}
                />
            );
        }
        return sqlEditor;
    }, [
        isFlinkSqlQuery,
        expressionObj,
        onValueChange,
        readOnly,
        variableTypes,
        sqlEditor,
        editedNode.outputVar,
        setNodeDataAt,
        outputVarErrors,
        fieldErrors,
        showValidation,
    ]);

    const isFixedValues = useMemo(
        () =>
            editors.some(
                (editor) =>
                    editor?.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR ||
                    editor?.type === EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR,
            ),
        [editors],
    );

    if (isFixedValues) return <>{editor}</>;

    return (
        <ExpressionTestResults fieldName={fieldName} resultsToShow={testResultsToShow}>
            {editor}
        </ExpressionTestResults>
    );
}

export default ExpressionField;
