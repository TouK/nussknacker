import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import React, { useCallback } from "react";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";
import { ActivityNodeParameters, ActivityParameterConfig } from "../../types/activity";
import { NodesDeploymentData } from "../../http/HttpService";
import { NodeValidationError } from "../../types";
import { default as EditableEditor } from "../graph/node-modal/editors/EditableEditor";

interface Props {
    nodeName: string;
    propertyName: string;
    propertyConfig: ActivityParameterConfig;
    nodesData: NodesDeploymentData;
    onChange: <K extends keyof ActivityNodeParameters["parameters"]>(
        nodeId: string,
        property: K,
        newValue: ActivityNodeParameters["parameters"][K],
        defaultValue?: ActivityNodeParameters["parameters"][K],
    ) => void;
    errors: NodeValidationError[];
}

export function ActivityProperty(props: Props): JSX.Element {
    const { nodeName, propertyName, propertyConfig, errors, nodesData, onChange } = props;

    const current = nodesData[nodeName][propertyName] || "";
    const expressionObj = { expression: current, value: current, language: ExpressionLang.String };
    const onValueChange = useCallback((newValue) => onChange(nodeName, propertyName, newValue), [onChange, nodeName, propertyName]);

    return (
        <EditableEditor
            key={propertyName}
            param={propertyConfig}
            fieldLabel={propertyConfig.label || propertyName}
            onValueChange={onValueChange}
            expressionObj={expressionObj}
            renderFieldLabel={() => (
                <FieldLabel title={propertyConfig.label} label={propertyConfig.label} hintText={propertyConfig.hintText} />
            )}
            readOnly={false}
            showSwitch={false}
            showValidation={true}
            //ScenarioProperties do not use any variables
            variableTypes={{}}
            fieldErrors={getValidationErrorsForField(errors, propertyName)}
        />
    );
}
