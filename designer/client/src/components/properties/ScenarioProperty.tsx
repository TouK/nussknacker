import { get } from "lodash";
import React, { useCallback } from "react";

import type { PropertiesType } from "../../types/node";
import type { UIScenarioProperty } from "../../types/scenarioGraph";
import type { NodeValidationError } from "../../types/validation";
import EditableEditor from "../graph/node-modal/editors/EditableEditor";
import type { ExpressionObj } from "../graph/node-modal/editors/expression/types";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";

export interface ScenarioPropertyProps {
    showSwitch?: boolean;
    showValidation?: boolean;
    propertyName: string;
    propertyConfig: UIScenarioProperty;
    editedNode: PropertiesType;
    onChange: <K extends keyof PropertiesType["additionalFields"]["properties"]>(
        property: K,
        newValue: PropertiesType["additionalFields"]["properties"][K],
        defaultValue?: PropertiesType["additionalFields"]["properties"][K],
    ) => void;
    readOnly?: boolean;
    errors: NodeValidationError[];
    isValidating: boolean;
}

export default function ScenarioProperty(props: ScenarioPropertyProps) {
    const { showSwitch, showValidation, propertyName, propertyConfig, errors, editedNode, onChange, readOnly, isValidating } = props;

    const propertyPath = `additionalFields.properties.${propertyName}`;
    const current = get(editedNode, propertyPath) || "";
    const expressionObj = { expression: current, value: current, language: ExpressionLang.String };

    const onValueChange = useCallback((newValue: ExpressionObj) => onChange(propertyPath, newValue.expression), [onChange, propertyPath]);

    return (
        <EditableEditor
            editors={[propertyConfig.editor]}
            defaultValue={propertyConfig.defaultValue}
            fieldLabel={propertyConfig.label || propertyName}
            onValueChange={onValueChange}
            expressionObj={expressionObj}
            readOnly={readOnly}
            key={propertyName}
            showSwitch={showSwitch}
            showValidation={showValidation}
            //ScenarioProperties do not use any variables
            variableTypes={{}}
            fieldErrors={getValidationErrorsForField(errors, propertyName)}
            isValidating={isValidating}
        />
    );
}
