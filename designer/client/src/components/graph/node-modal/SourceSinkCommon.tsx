/* eslint-disable i18next/no-literal-string */
import type { PropsWithChildren } from "react";
import React from "react";

import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DescriptionField } from "./DescriptionField";
import { NameField } from "./NameField";
import { findParameters } from "./NodeDetailsContent/helpers";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";
import { ParametersListWithOverrides } from "./ParametersListWithOverrides";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

const getListFieldPath = (index: number) => `ref.parameters[${index}]`;

interface SourceSinkCommonProps {
    errors: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}

export const SourceSinkCommon = ({
    children,
    errors,
    isEditMode,
    node,
    parameterDefinitions,
    setProperty,
    showSwitch,
    showValidation,
}: PropsWithChildren<SourceSinkCommonProps>): React.JSX.Element => {
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    return (
        <>
            <NameField isEditMode={isEditMode} showValidation={showValidation} node={node} setProperty={setProperty} errors={errors} />
            <ParametersListWithOverrides
                parameters={findParameters(node)}
                isEditMode={isEditMode}
                showValidation={showValidation}
                showSwitch={showSwitch}
                node={node}
                findAvailableVariables={findAvailableVariables}
                parameterDefinitions={parameterDefinitions}
                errors={errors}
                setProperty={setProperty}
                getListFieldPath={getListFieldPath}
            >
                {children}
                <DescriptionField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    node={node}
                    setProperty={setProperty}
                    errors={errors}
                />
            </ParametersListWithOverrides>
        </>
    );
};
