import { get } from "lodash";
import EditableEditor from "./editors/EditableEditor";
import React, { useCallback, useMemo } from "react";
import { ExpressionLang } from "./editors/expression/types";
import { errorValidator, PossibleValue } from "./editors/Validators";
import { NodeType, NodeValidationError } from "../../../types";
import { ValidationError } from "../../modals/ValidationLabels";

export interface ScenarioPropertyConfig {
    editor: any;
    label: string;
    values: Array<PossibleValue>;
}

interface Props {
    showSwitch: boolean;
    showValidation: boolean;
    propertyName: string;
    propertyConfig: ScenarioPropertyConfig;
    editedNode: NodeType;
    onChange: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    renderFieldLabel: (paramName: string) => JSX.Element;
    readOnly: boolean;
    fieldErrors: NodeValidationError[];
}

export default function ScenarioProperty(props: Props) {
    const { showSwitch, showValidation, propertyName, propertyConfig, fieldErrors, editedNode, onChange, renderFieldLabel, readOnly } =
        props;

    const propertyPath = `additionalFields.properties.${propertyName}`;
    const current = get(editedNode, propertyPath) || "";
    const expressionObj = { expression: current, value: current, language: ExpressionLang.String };

    const onValueChange = useCallback((newValue) => onChange(propertyPath, newValue), [onChange, propertyName]);

    return (
        <EditableEditor
            param={propertyConfig}
            fieldName={propertyName}
            fieldLabel={propertyConfig.label || propertyName}
            onValueChange={onValueChange}
            expressionObj={expressionObj}
            renderFieldLabel={renderFieldLabel}
            readOnly={readOnly}
            key={propertyName}
            showSwitch={showSwitch}
            showValidation={showValidation}
            //ScenarioProperties do not use any variables
            variableTypes={{}}
            fieldErrors={fieldErrors}
        />
    );
}
