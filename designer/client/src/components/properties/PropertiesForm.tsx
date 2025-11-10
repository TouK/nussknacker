import { sortBy } from "lodash";
import type { ComponentProps } from "react";
import React, { useMemo } from "react";

import HttpService from "../../http/HttpService/instance";
import { useAppSelector } from "../../store/storeHelpers";
import type { PropertiesType } from "../../types/node";
import type { NodeValidationError } from "../../types/validation";
import { DescriptionField } from "../graph/node-modal/DescriptionField";
import { FieldType } from "../graph/node-modal/editors/field/Field";
import { FieldLabelProvider } from "../graph/node-modal/editors/RenderFieldLabel";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import NodeAdditionalInfoBox from "../graph/node-modal/NodeAdditionalInfoBox";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { getScenarioPropertiesConfig } from "../graph/node-modal/NodeDetailsContent/selectors";
import { NodeField } from "../graph/node-modal/NodeField";
import { NameField } from "./NameField";
import ScenarioProperty from "./ScenarioProperty";

interface Props {
    errors?: NodeValidationError[];
    handleSetEditedProperties?: ComponentProps<typeof ScenarioProperty>["onChange"];
    editedProperties: PropertiesType;
    showSwitch?: boolean;
}
export const PropertiesForm = ({ errors = [], handleSetEditedProperties, editedProperties, showSwitch = false }: Props) => {
    const readOnly = !handleSetEditedProperties;
    const scenarioProperties = useAppSelector(getScenarioPropertiesConfig);
    const scenarioPropertiesConfig = useMemo(() => scenarioProperties?.propertiesConfig ?? {}, [scenarioProperties?.propertiesConfig]);

    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const scenarioPropertiesSorted = useMemo(
        () => sortBy(Object.entries(scenarioPropertiesConfig), ([name]) => name),
        [scenarioPropertiesConfig],
    );

    const renderFieldLabel1 = (paramName: string) => <FieldLabel title={paramName} label={paramName} />;

    return (
        <NodeTable>
            <NameField errors={errors} onChange={handleSetEditedProperties} readOnly={readOnly} value={editedProperties.name} />
            {scenarioPropertiesSorted.map(([propName, propConfig]) => {
                const renderFieldLabel2 = () => (
                    <FieldLabel title={propConfig.label} label={propConfig.label} hintText={propConfig.hintText} />
                );
                return (
                    <FieldLabelProvider value={renderFieldLabel2} key={propName}>
                        <ScenarioProperty
                            showSwitch={showSwitch}
                            showValidation
                            propertyName={propName}
                            propertyConfig={propConfig}
                            errors={errors}
                            onChange={handleSetEditedProperties}
                            editedNode={editedProperties}
                            readOnly={readOnly}
                        />
                    </FieldLabelProvider>
                );
            })}
            <FieldLabelProvider value={renderFieldLabel1}>
                <DescriptionField
                    isEditMode={!readOnly}
                    showValidation
                    node={editedProperties}
                    setProperty={handleSetEditedProperties}
                    errors={errors}
                />
                <NodeField
                    isEditMode={!readOnly}
                    showValidation
                    node={editedProperties}
                    setProperty={handleSetEditedProperties}
                    errors={errors}
                    fieldType={FieldType.checkbox}
                    fieldName={"additionalFields.showDescription"}
                    description={"Show description each time scenario is opened"}
                />
            </FieldLabelProvider>
            <NodeAdditionalInfoBox node={editedProperties} handleGetAdditionalInfo={HttpService.getPropertiesAdditionalInfo} />
        </NodeTable>
    );
};
