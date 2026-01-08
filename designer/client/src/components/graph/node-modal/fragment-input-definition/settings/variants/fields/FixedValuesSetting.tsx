import React from "react";

import type { ReturnedType } from "../../../../../../../types/scenarioGraph";
import type { NodeValidationError, VariableTypes } from "../../../../../../../types/validation";
import type { FixedListParameterVariant, FixedValuesOption, onChangeType } from "../../../item/types";
import { FixedValuesType } from "../../../item/types";
import { DictSelect } from "./dictSelect";
import { UserDefinedListInput } from "./UserDefinedListInput";

interface FixedValuesSetting extends Pick<FixedListParameterVariant, "presetSelection"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesType: FixedValuesType;
    fixedValuesList: FixedValuesOption[];
    dictId: string;
    readOnly: boolean;
    variableTypes: VariableTypes;
    errors: NodeValidationError[];
    typ: ReturnedType;
    name: string;
    initialValue: FixedValuesOption;
    userDefinedListInputLabel: string;
}

export function FixedValuesSetting({
    path,
    fixedValuesType,
    onChange,
    dictId,
    fixedValuesList,
    readOnly,
    variableTypes,
    errors,
    typ,
    name,
    initialValue,
    userDefinedListInputLabel,
}: FixedValuesSetting) {
    return (
        <>
            {fixedValuesType === FixedValuesType.ValueInputWithDictEditor && (
                <DictSelect typ={typ} name={name} dictId={dictId} readOnly={readOnly} onChange={onChange} path={path} errors={errors} />
            )}
            {fixedValuesType === FixedValuesType.ValueInputWithFixedValuesProvided && (
                <UserDefinedListInput
                    fixedValuesList={fixedValuesList}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    onChange={onChange}
                    path={path}
                    errors={errors}
                    typ={typ}
                    name={name}
                    initialValue={initialValue}
                    inputLabel={userDefinedListInputLabel}
                />
            )}
        </>
    );
}
