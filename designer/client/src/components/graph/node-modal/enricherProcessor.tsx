import { NodeType, NodeValidationError, UIParameter } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import { IdField } from "./IdField";
import { serviceParameters } from "./NodeDetailsContent/helpers";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { NodeField } from "./NodeField";
import { FieldType } from "./editors/field/Field";
import { DisableField } from "./DisableField";
import { DescriptionField } from "./DescriptionField";
import React from "react";

export function EnricherProcessor({
    errors,
    findAvailableVariables,
    isEditMode,
    node,
    parameterDefinitions,
    renderFieldLabel,
    setProperty,
    showSwitch,
    showValidation,
}: {
    errors: NodeValidationError[];
    findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
}): JSX.Element {
    return (
        <>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                setProperty={setProperty}
                renderFieldLabel={renderFieldLabel}
                errors={errors}
            />
            {serviceParameters(node).map((param, index) => {
                return (
                    <div className="node-block" key={node.id + param.name + index}>
                        <ParameterExpressionField
                            isEditMode={isEditMode}
                            showValidation={showValidation}
                            showSwitch={showSwitch}
                            node={node}
                            findAvailableVariables={findAvailableVariables}
                            parameterDefinitions={parameterDefinitions}
                            errors={errors}
                            renderFieldLabel={renderFieldLabel}
                            setProperty={setProperty}
                            parameter={param}
                            listFieldPath={`service.parameters[${index}]`}
                        />
                    </div>
                );
            })}
            {node.type === "Enricher" ? (
                <NodeField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    fieldType={FieldType.input}
                    fieldLabel={"Output"}
                    fieldName={"output"}
                    errors={errors}
                />
            ) : null}
            {node.type === "Processor" ? (
                <DisableField
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    errors={errors}
                />
            ) : null}
            <DescriptionField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
