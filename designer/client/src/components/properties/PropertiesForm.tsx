import React from "react";

import type { PropertiesType } from "../../types/node";
import type { PropertiesConfigKeys } from "../../types/scenarioGraph";
import type { NodeValidationError } from "../../types/validation";
import { DescriptionField } from "../graph/node-modal/DescriptionField";
import { FieldType } from "../graph/node-modal/editors/field/Field";
import { FieldLabelProvider } from "../graph/node-modal/editors/RenderFieldLabel";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import { PropertiesAdditionalInfo } from "../graph/node-modal/NodeAdditionalInfo";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { NodeField } from "../graph/node-modal/NodeField";
import { NameField } from "./NameField";
import { PropertiesFormFields } from "./PropertiesFormFields";
import type { ScenarioPropertyProps } from "./ScenarioProperty";

export interface PropertiesFormProps {
    errors?: NodeValidationError[];
    handleSetEditedProperties?: ScenarioPropertyProps["onChange"];
    editedProperties: PropertiesType;
    showSwitch?: boolean;
    pick?: PropertiesConfigKeys[];
}

export const PropertiesForm = ({
    pick,
    errors = [],
    handleSetEditedProperties,
    editedProperties,
    showSwitch = false,
}: PropertiesFormProps) => {
    const readOnly = !handleSetEditedProperties;
    return (
        <NodeTable>
            <FieldLabelProvider value={(paramName) => <FieldLabel label={paramName} />}>
                <NameField errors={errors} onChange={handleSetEditedProperties} readOnly={readOnly} value={editedProperties.name} />
                <PropertiesFormFields
                    pick={pick}
                    showSwitch={showSwitch}
                    errors={errors}
                    handleSetEditedProperties={handleSetEditedProperties}
                    editedProperties={editedProperties}
                    readOnly={readOnly}
                />
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
            <PropertiesAdditionalInfo />
        </NodeTable>
    );
};
