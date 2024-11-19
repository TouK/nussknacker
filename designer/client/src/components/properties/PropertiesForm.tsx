import React, { ComponentProps, useMemo } from "react";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { FieldType } from "../graph/node-modal/editors/field/Field";
import { sortBy } from "lodash";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import ScenarioProperty from "./ScenarioProperty";
import { DescriptionField } from "../graph/node-modal/DescriptionField";
import { NodeField } from "../graph/node-modal/NodeField";
import NodeAdditionalInfoBox from "../graph/node-modal/NodeAdditionalInfoBox";
import HttpService from "../../http/HttpService";
import { NodeValidationError, PropertiesType } from "../../types";
import { useSelector } from "react-redux";
import { getScenarioPropertiesConfig } from "../graph/node-modal/NodeDetailsContent/selectors";
import { NameField } from "./NameField";

interface Props {
    errors?: NodeValidationError[];
    handleSetEditedProperties?: ComponentProps<typeof ScenarioProperty>["onChange"];
    editedProperties: PropertiesType;
    showSwitch?: boolean;
}
export const PropertiesForm = ({ errors = [], handleSetEditedProperties, editedProperties, showSwitch = false }: Props) => {
    const readOnly = !handleSetEditedProperties;
    const scenarioProperties = useSelector(getScenarioPropertiesConfig);
    const scenarioPropertiesConfig = useMemo(() => scenarioProperties?.propertiesConfig ?? {}, [scenarioProperties?.propertiesConfig]);

    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const scenarioPropertiesSorted = useMemo(
        () => sortBy(Object.entries(scenarioPropertiesConfig), ([name]) => name),
        [scenarioPropertiesConfig],
    );

    return (
        <NodeTable>
            <NameField errors={errors} onChange={handleSetEditedProperties} readOnly={readOnly} value={editedProperties.name} />
            {scenarioPropertiesSorted.map(([propName, propConfig]) => (
                <ScenarioProperty
                    key={propName}
                    showSwitch={showSwitch}
                    showValidation
                    propertyName={propName}
                    propertyConfig={propConfig}
                    errors={errors}
                    onChange={handleSetEditedProperties}
                    renderFieldLabel={() => <FieldLabel title={propConfig.label} label={propConfig.label} hintText={propConfig.hintText} />}
                    editedNode={editedProperties}
                    readOnly={readOnly}
                />
            ))}
            <DescriptionField
                isEditMode={!readOnly}
                showValidation
                node={editedProperties}
                renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                setProperty={handleSetEditedProperties}
                errors={errors}
            />
            <NodeField
                isEditMode={!readOnly}
                showValidation
                node={editedProperties}
                renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                setProperty={handleSetEditedProperties}
                errors={errors}
                fieldType={FieldType.checkbox}
                fieldName={"additionalFields.showDescription"}
                description={"Show description each time scenario is opened"}
            />
            <NodeAdditionalInfoBox node={editedProperties} handleGetAdditionalInfo={HttpService.getPropertiesAdditionalInfo} />
        </NodeTable>
    );
};
