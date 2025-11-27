import type { PropsWithChildren } from "react";
import React, { useMemo } from "react";

import ProcessUtils from "../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { AggregateParametersList } from "./aggregateParametersList";
import { DescriptionField } from "./DescriptionField";
import { FieldType } from "./editors/field/Field";
import { IdField } from "./IdField";
import { isAggregate } from "./isAggregate";
import { findParameters } from "./NodeDetailsContent/helpers";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";
import { NodeField } from "./NodeField";
import { ParametersListAdvanced } from "./parametersListAdvanced";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

export type CustomNodeProps = {
    errors: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
};

export function CustomNode({
    children,
    errors,
    isEditMode,
    node,
    parameterDefinitions,
    setProperty,
    showSwitch,
    showValidation,
}: PropsWithChildren<CustomNodeProps>): React.JSX.Element {
    const processDefinitionData = useAppSelector(getProcessDefinitionData);
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const hasOutputVar = useMemo(
        (): boolean => !!ProcessUtils.extractComponentDefinition(node, processDefinitionData.components)?.returnType || !!node.outputVar,
        [node, processDefinitionData.components],
    );

    const ParametersComponent = useMemo(() => {
        return isAggregate(node) ? AggregateParametersList : ParametersListAdvanced;
    }, [node]);

    return (
        <>
            <IdField node={node} isEditMode={isEditMode} showValidation={showValidation} setProperty={setProperty} errors={errors} />
            {hasOutputVar && (
                <NodeField
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    setProperty={setProperty}
                    fieldType={FieldType.input}
                    fieldLabel={"Output variable name"}
                    fieldName={"outputVar"}
                    errors={errors}
                />
            )}
            {children}
            <ParametersComponent
                parameters={findParameters(node)}
                showSwitch={showSwitch}
                findAvailableVariables={findAvailableVariables}
                parameterDefinitions={parameterDefinitions}
                errors={errors}
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                setProperty={setProperty}
                getListFieldPath={(index: number) => `parameters[${index}]`}
            >
                <DescriptionField
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    setProperty={setProperty}
                    errors={errors}
                />
            </ParametersComponent>
        </>
    );
}
