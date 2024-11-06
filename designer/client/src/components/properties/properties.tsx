import React, { ComponentProps, useMemo } from "react";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import Field, { FieldType } from "../graph/node-modal/editors/field/Field";
import { isEmpty, sortBy } from "lodash";
import { nodeInput, nodeInputWithError } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import ScenarioProperty from "./ScenarioProperty";
import { DescriptionField } from "../graph/node-modal/DescriptionField";
import { NodeField } from "../graph/node-modal/NodeField";
import NodeAdditionalInfoBox from "../graph/node-modal/NodeAdditionalInfoBox";
import HttpService from "../../http/HttpService";
import { NodeValidationError, PropertiesType } from "../../types";
import { useSelector } from "react-redux";
import { getScenarioPropertiesConfig } from "../graph/node-modal/NodeDetailsContent/selectors";

interface Props {
    isEditMode?: boolean;
    errors?: NodeValidationError[];
    handleSetEditedProperties?: ComponentProps<typeof ScenarioProperty>["onChange"];
    editedProperties: PropertiesType;
    showSwitch?: boolean;
}
export const Properties = ({ isEditMode = false, errors = [], handleSetEditedProperties, editedProperties, showSwitch = false }: Props) => {
    const scenarioProperties = useSelector(getScenarioPropertiesConfig);
    const scenarioPropertiesConfig = useMemo(() => scenarioProperties?.propertiesConfig ?? {}, [scenarioProperties?.propertiesConfig]);

    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const scenarioPropertiesSorted = useMemo(
        () => sortBy(Object.entries(scenarioPropertiesConfig), ([name]) => name),
        [scenarioPropertiesConfig],
    );

    return (
        <NodeTable>
            <Field
                type={FieldType.input}
                isMarked={false}
                showValidation
                onChange={(newValue) => handleSetEditedProperties("name", newValue.toString())}
                readOnly={!isEditMode}
                className={isEmpty(errors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`}
                fieldErrors={getValidationErrorsForField(errors, "name")}
                value={editedProperties.name}
                autoFocus
            >
                <FieldLabel title={"Name"} label={"Name"} />
            </Field>
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
                    readOnly={!isEditMode}
                />
            ))}
            <DescriptionField
                isEditMode={isEditMode}
                showValidation
                node={editedProperties}
                renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                setProperty={handleSetEditedProperties}
                errors={errors}
            />
            <NodeField
                isEditMode={isEditMode}
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
