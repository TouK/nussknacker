import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import React, { useCallback } from "react";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";
import { ActionParameterConfig, ActionParameterName } from "../../types/action";
import { NodeValidationError } from "../../types";
import { default as EditableEditor } from "../graph/node-modal/editors/EditableEditor";

interface Props {
    nodeId: string;
    parameterName: string;
    parameterConfig: ActionParameterConfig;
    parameterValue: string;
    onChange: (nodeId: string, parameterName: ActionParameterName, newValue: string) => void;
    errors: NodeValidationError[];
}

export function ActionParameter(props: Props): JSX.Element {
    const { nodeId, parameterName, parameterConfig, errors, parameterValue, onChange } = props;

    const expressionObj = { expression: parameterValue, value: parameterValue, language: ExpressionLang.String };
    const onValueChange = useCallback((newValue: string) => onChange(nodeId, parameterName, newValue), [onChange, nodeId, parameterName]);

    return (
        <EditableEditor
            key={parameterName}
            param={parameterConfig}
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
