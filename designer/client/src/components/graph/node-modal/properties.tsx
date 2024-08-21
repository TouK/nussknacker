import { NodeType, NodeValidationError, PropertiesType } from "../../../types";
import { useSelector } from "react-redux";
import { getScenarioPropertiesConfig } from "./NodeDetailsContent/selectors";
import React, { useMemo } from "react";
import { sortBy } from "lodash";
import { IdField } from "./IdField";
import ScenarioProperty from "./ScenarioProperty";
import { DescriptionField } from "./DescriptionField";
import { FieldLabel } from "./FieldLabel";
import { FieldType } from "./editors/field/Field";
import { NodeField } from "./NodeField";

interface Props {
    isEditMode?: boolean;
    node: PropertiesType;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    errors?: NodeValidationError[];
    showValidation?: boolean;
}

export function Properties({
    errors = [],
    isEditMode,
    node,
    renderFieldLabel,
    setProperty,
    showSwitch,
    showValidation,
}: Props): JSX.Element {
    const scenarioProperties = useSelector(getScenarioPropertiesConfig);
    const scenarioPropertiesConfig = scenarioProperties?.propertiesConfig ?? {};

    //fixme move this configuration to some better place?
    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const scenarioPropertiesSorted = useMemo(
        () => sortBy(Object.entries(scenarioPropertiesConfig), ([name]) => name),
        [scenarioPropertiesConfig],
    );

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
            {scenarioPropertiesSorted.map(([propName, propConfig]) => (
                <ScenarioProperty
                    key={propName}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                    propertyName={propName}
                    propertyConfig={propConfig}
                    errors={errors}
                    onChange={setProperty}
                    renderFieldLabel={() => <FieldLabel title={propConfig.label} label={propConfig.label} hintText={propConfig.hintText} />}
                    editedNode={node}
                    readOnly={!isEditMode}
                />
            ))}
            <DescriptionField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            <NodeField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
                fieldType={FieldType.checkbox}
                fieldName={"additionalFields.showDescription"}
                description={"Show description each time scenario is opened"}
            />
        </>
    );
}
