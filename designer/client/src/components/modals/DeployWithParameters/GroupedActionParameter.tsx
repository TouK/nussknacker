import React, { useCallback } from "react";

import type { ActionParameterConfig, ActionParameterName } from "../../../types/action";
import type { NodeValidationError } from "../../../types/validation";
import { default as EditableEditor } from "../../graph/node-modal/editors/EditableEditor";
import type { ExpressionObj } from "../../graph/node-modal/editors/expression/types";
import { ExpressionLang } from "../../graph/node-modal/editors/expression/types";
import { FieldLabelProvider } from "../../graph/node-modal/editors/RenderFieldLabel";
import { getValidationErrorsForField } from "../../graph/node-modal/editors/Validators";
import { FieldLabel } from "../../graph/node-modal/FieldLabel";

interface Props {
    nodeIds: string[];
    parameterName: string;
    parameterConfig: ActionParameterConfig;
    parameterValue: string;
    onChange: (nodeIds: string[], parameterName: ActionParameterName, newValue: string) => void;
    errors: NodeValidationError[];
    isValidating: boolean;
}

export function GroupedActionParameter(props: Props): React.JSX.Element {
    const { nodeIds, parameterName, parameterConfig, errors, parameterValue, onChange, isValidating } = props;

    const expressionObj = { expression: parameterValue, value: parameterValue, language: ExpressionLang.String };
    const onValueChange = useCallback(
        (newValue: ExpressionObj) => onChange(nodeIds, parameterName, newValue.expression),
        [onChange, nodeIds, parameterName],
    );

    const renderFieldLabel = () => (
        <FieldLabel title={parameterConfig.label} label={parameterConfig.label} hintText={parameterConfig.hintText} />
    );
    return (
        <FieldLabelProvider value={renderFieldLabel} key={parameterName}>
            <EditableEditor
                editors={[parameterConfig.editor]}
                fieldLabel={parameterConfig.label || parameterName}
                onValueChange={onValueChange}
                expressionObj={expressionObj}
                readOnly={false}
                showSwitch={false}
                showValidation={true}
                //ScenarioProperties do not use any variables
                variableTypes={{}}
                fieldErrors={getValidationErrorsForField(errors, parameterName)}
                isValidating={isValidating}
            />
        </FieldLabelProvider>
    );
}
