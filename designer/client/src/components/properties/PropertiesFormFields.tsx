import React from "react";

import { useAppSelector } from "../../store/storeHelpers";
import { FieldLabelProvider } from "../graph/node-modal/editors/RenderFieldLabel";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import { getScenarioPropertiesConfig } from "../graph/node-modal/NodeDetailsContent/selectors";
import type { PropertiesFormProps } from "./PropertiesForm";
import ScenarioProperty from "./ScenarioProperty";

interface PropertiesFormFieldsProps extends PropertiesFormProps {
    readOnly?: boolean;
}

export const PropertiesFormFields = ({
    showSwitch,
    errors,
    handleSetEditedProperties,
    editedProperties,
    readOnly,
    pick,
    isValidating,
}: PropertiesFormFieldsProps) => {
    const { properties, order } = useAppSelector(getScenarioPropertiesConfig);
    return (
        <>
            {order
                .filter((name) => (pick ? pick.includes(name) : true))
                .map((name) => (
                    <FieldLabelProvider
                        key={name}
                        value={() => <FieldLabel label={properties[name].label} hintText={properties[name].hintText} />}
                    >
                        <ScenarioProperty
                            showValidation
                            propertyName={name}
                            propertyConfig={properties[name]}
                            onChange={handleSetEditedProperties}
                            editedNode={editedProperties}
                            errors={errors}
                            showSwitch={showSwitch}
                            readOnly={readOnly}
                            isValidating={isValidating}
                        />
                    </FieldLabelProvider>
                ))}
        </>
    );
};
