import React from "react";
import { useTranslation } from "react-i18next";

import type ProcessUtils from "../../../common/ProcessUtils";
import { useUserSettings } from "../../../common/userSettings";
import type { NodeType, NodeValidationError, UIParameter } from "../../../types";
import { DescriptionField } from "./DescriptionField";
import { DisableField } from "./DisableField";
import MockExpressionField from "./editors/expression/MockExpressionField";
import { FieldType } from "./editors/field/Field";
import { IdField } from "./IdField";
import { findParameters } from "./NodeDetailsContent/helpers";
import { NodeField } from "./NodeField";
import { ParametersListAdvanced } from "./parametersListAdvanced";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

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
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}): JSX.Element {
    const { t } = useTranslation();
    const [settings] = useUserSettings();
    const showMockFieldOnEnrichers = settings["node.showMockFieldOnEnrichers"];

    const showMockField = showMockFieldOnEnrichers && node.type === "Enricher" && node.service.id !== "decision-table";

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
                getListFieldPath={(index: number) => `service.parameters[${index}]`}
            >
                {node.type === "Enricher" ? (
                    <NodeField
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        node={node}
                        renderFieldLabel={renderFieldLabel}
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
                {showMockField ? (
                    <MockExpressionField
                        isEditMode={isEditMode}
                        editedNode={node}
                        showValidation={showValidation}
                        showSwitch={showSwitch}
                        findAvailableVariables={findAvailableVariables}
                        setNodeDataAt={setProperty}
                        errors={errors}
                    />
                ) : null}
            </ParametersListAdvanced>
        </>
    );
}
