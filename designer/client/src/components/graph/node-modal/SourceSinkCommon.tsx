/* eslint-disable i18next/no-literal-string */
import React, { PropsWithChildren } from "react";
import { IdField } from "./IdField";
import { DescriptionField } from "./DescriptionField";
import { NodeType, NodeValidationError, UIParameter } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import { ParametersList } from "./parametersList";

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
            <ParametersList
                parameters={node.ref.parameters}
                isEditMode={isEditMode}
                showValidation={showValidation}
                showSwitch={showSwitch}
                node={node}
                findAvailableVariables={findAvailableVariables}
                parameterDefinitions={parameterDefinitions}
                errors={errors}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                getListFieldPath={(index: number) => `ref.parameters[${index}]`}
            />
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
