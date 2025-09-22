/* eslint-disable i18next/no-literal-string */
import type { PropsWithChildren } from "react";
import React from "react";

import type ProcessUtils from "../../../common/ProcessUtils";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DescriptionField } from "./DescriptionField";
import { IdField } from "./IdField";
import { findParameters } from "./NodeDetailsContent/helpers";
import { ParametersListAdvanced } from "./parametersListAdvanced";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

const getListFieldPath = (index: number) => `ref.parameters[${index}]`;

interface SourceSinkCommonProps {
    errors: NodeValidationError[];
    findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
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
            <ParametersListAdvanced
                parameters={findParameters(node)}
                isEditMode={isEditMode}
                showValidation={showValidation}
                showSwitch={showSwitch}
                node={node}
                findAvailableVariables={findAvailableVariables}
                parameterDefinitions={parameterDefinitions}
                errors={errors}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                getListFieldPath={getListFieldPath}
            >
                {children}
                <DescriptionField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    errors={errors}
                />
            </ParametersListAdvanced>
        </>
    );
};
