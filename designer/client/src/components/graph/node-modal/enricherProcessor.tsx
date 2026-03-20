import React from "react";
import { useTranslation } from "react-i18next";

import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DescriptionField } from "./DescriptionField";
import { DisableField } from "./DisableField";
import { FieldType } from "./editors/field/Field";
import { IdField } from "./IdField";
import { findParameters } from "./NodeDetailsContent/helpers";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";
import { NodeField } from "./NodeField";
import { ParametersListWithOverrides } from "./ParametersListWithOverrides";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

export function EnricherProcessor({
    errors,
    isEditMode,
    node,
    parameterDefinitions,
    setProperty,
    showSwitch,
    showValidation,
}: {
    errors: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}): React.JSX.Element {
    const { t } = useTranslation();
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);

    return (
        <>
            <IdField isEditMode={isEditMode} showValidation={showValidation} node={node} setProperty={setProperty} errors={errors} />
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
                getListFieldPath={(index: number) => `service.parameters[${index}]`}
            >
                {node.type === "Enricher" ? (
                    <NodeField
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        node={node}
                        setProperty={setProperty}
                        fieldType={FieldType.input}
                        fieldLabel={t("nodes.enricher.output", "Output variable name")}
                        fieldName={"output"}
                        errors={errors}
                    />
                ) : null}
                {node.type === "Processor" ? (
                    <DisableField
                        node={node}
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        setProperty={setProperty}
                        errors={errors}
                    />
                ) : null}
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
