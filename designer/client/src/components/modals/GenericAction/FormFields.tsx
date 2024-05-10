import React, { useContext } from "react";
import { GenericActionFormContext } from "./GenericActionFormContext";
import { FormField } from "./FormField";

export function FormFields() {
    const { parameters = [] } = useContext(GenericActionFormContext);

    return (
        // TODO: investigate why there are problems with tabindex when parameters are wrapped by DOM element
        <>
            {parameters.map(({ name }) => (
                <FormField key={name} name={name} />
            ))}
        </>
    );
}
