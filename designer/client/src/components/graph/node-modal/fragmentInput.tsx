import React, { useCallback } from "react";

import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DescriptionField } from "./DescriptionField";
import { DisableField } from "./DisableField";
import { NameField } from "./NameField";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";
import OutputParametersList from "./OutputParametersList";
import { ParametersListWithOverrides } from "./ParametersListWithOverrides";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";
import { useParametersList } from "./useParametersList";

interface FragmentInput {
    errors: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}

const getListFieldPath = (index: number) => `ref.parameters[${index}]`;

export function FragmentInput(props: FragmentInput): React.JSX.Element {
    const { errors, isEditMode, node, parameterDefinitions, setProperty, showSwitch, showValidation } = props;
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const processDefinitionData = useAppSelector(getProcessDefinitionData);

    const setNodeState = useCallback((newParams) => setProperty("ref.parameters", newParams), [setProperty]);
    const parameters = useParametersList(node, processDefinitionData, isEditMode, setNodeState);

    return (
        <>
            <NameField node={node} isEditMode={isEditMode} showValidation={showValidation} setProperty={setProperty} errors={errors} />
            <DisableField node={node} isEditMode={isEditMode} showValidation={showValidation} setProperty={setProperty} errors={errors} />
            <ParametersListWithOverrides
                parameters={parameters}
                showSwitch={showSwitch}
                findAvailableVariables={findAvailableVariables}
                parameterDefinitions={parameterDefinitions}
                errors={errors}
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                setProperty={setProperty}
                getListFieldPath={getListFieldPath}
            >
                <OutputParametersList
                    editedNode={node}
                    errors={errors}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    setProperty={setProperty}
                    processDefinitionData={processDefinitionData}
                />
                <DescriptionField
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    setProperty={setProperty}
                    errors={errors}
                />
            </ParametersListWithOverrides>
        </>
    );
}
