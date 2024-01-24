/* eslint-disable i18next/no-literal-string */
import React, { PropsWithChildren } from "react";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { IdField } from "./IdField";
import { DescriptionField } from "./DescriptionField";
import { NodeType, NodeValidationError, UIParameter } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";

interface SourceSinkCommonProps {
    errors: NodeValidationError[];
    findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
}

export const SourceSinkCommon = ({
    children,
    errors,
    findAvailableVariables,
    isEditMode,
    node,
    parameterDefinitions,
    renderFieldLabel,
    setProperty,
    showSwitch,
    showValidation,
}: PropsWithChildren<SourceSinkCommonProps>): JSX.Element => {
    return (
        <>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            {node.ref.parameters?.map((param, index) => (
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
                        listFieldPath={`ref.parameters[${index}]`}
                    />
                </div>
            ))}
            {children}
            <DescriptionField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
};
