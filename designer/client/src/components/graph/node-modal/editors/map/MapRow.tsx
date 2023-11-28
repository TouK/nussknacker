import React, { useContext } from "react";
import { Field } from "../../../../../types";
import { errorValidator } from "../Validators";
import { MapItemsCtx } from "./Map";
import MapKey from "./MapKey";
import MapValue from "./MapValue";

interface MapRowProps<F extends Field> {
    index: number;
    item: F;
}

type TypedField = Field & {
    typeInfo: string;
};

export default function MapRow<F extends TypedField>({ index, item }: MapRowProps<F>) {
    const { fieldErrors, isMarked, readOnly, setProperty, showValidation, variableTypes } = useContext(MapItemsCtx);
    const setItemProperty = (field: string, value) => setProperty(`[${index}].${field}`, value);
    const isPropertyMarked = (field: string) => isMarked(`[${index}].${field}`);
    const { typeInfo, name, expression } = item;
    return (
        <>
            <MapKey
                readOnly={readOnly}
                showValidation={showValidation}
                isMarked={isPropertyMarked("name")}
                onChange={(value) => setItemProperty("name", value)}
                value={name}
                validators={[errorValidator(fieldErrors, `$fields-${index}-key`)]}
            />
            <MapValue
                readOnly={readOnly}
                showValidation={showValidation}
                isMarked={isPropertyMarked("expression.expression")}
                onChange={(value) => setItemProperty("expression.expression", value)}
                value={expression}
                validationLabelInfo={typeInfo}
                errors={fieldErrors}
                variableTypes={variableTypes}
                validators={[errorValidator(fieldErrors, `$fields-${index}-value`)]}
            />
        </>
    );
}
