import React, { useCallback } from "react";

import type { NodeValidationError } from "../../../types";
import type { ActionParameterConfig, ActionParameterName } from "../../../types/action";
import { default as EditableEditor } from "../../graph/node-modal/editors/EditableEditor";
import type { ExpressionObj } from "../../graph/node-modal/editors/expression/types";
import { ExpressionLang } from "../../graph/node-modal/editors/expression/types";
import { getValidationErrorsForField } from "../../graph/node-modal/editors/Validators";
import { FieldLabel } from "../../graph/node-modal/FieldLabel";

interface Props {
    nodeIds: string[];
    parameterName: string;
    parameterConfig: ActionParameterConfig;
    parameterValue: string;
    onChange: (nodeIds: string[], parameterName: ActionParameterName, newValue: string) => void;
    errors: NodeValidationError[];
}

export function GroupedActionParameter(props: Props): JSX.Element {
    const { nodeIds, parameterName, parameterConfig, errors, parameterValue, onChange } = props;

    const expressionObj = { expression: parameterValue, value: parameterValue, language: ExpressionLang.String };
    const onValueChange = useCallback(
        (newValue: ExpressionObj) => onChange(nodeIds, parameterName, newValue.expression),
        [onChange, nodeIds, parameterName],
    );

    return (
        <EditableEditor
            key={parameterName}
            editors={[parameterConfig.editor]}
            fieldLabel={parameterConfig.label || parameterName}
            onValueChange={onValueChange}
            expressionObj={expressionObj}
            renderFieldLabel={() => (
                <FieldLabel title={parameterConfig.label} label={parameterConfig.label} hintText={parameterConfig.hintText} />
            )}
            readOnly={false}
            showSwitch={false}
            showValidation={true}
            //ScenarioProperties do not use any variables
            variableTypes={{}}
            fieldErrors={getValidationErrorsForField(errors, parameterName)}
        />
    );
}
