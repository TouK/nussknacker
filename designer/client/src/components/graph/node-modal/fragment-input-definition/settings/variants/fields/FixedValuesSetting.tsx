import React from "react";
import { FixedValuesType, onChangeType, FixedValuesOption, FixedListParameterVariant } from "../../../item";
import { NodeValidationError, ReturnedType, VariableTypes } from "../../../../../../../types";
import { UserDefinedListInput } from "./UserDefinedListInput";
import { DictSelect } from "./dictSelect";

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
                <DictSelect
                    typ={{ ...typ, refClazzName: "java.lang." + typ.refClazzName } as ReturnedType}
                    name={name}
                    dictId={dictId}
                    readOnly={readOnly}
                    onChange={onChange}
                    path={path}
                    errors={errors}
                />
            )}
            {fixedValuesType === FixedValuesType.ValueInputWithFixedValuesProvided && (
                <UserDefinedListInput
                    fixedValuesList={fixedValuesList}
                    variableTypes={variableTypes}
                    readOnly={readOnly}
                    onChange={onChange}
                    path={path}
                    errors={errors}
                    typ={{ ...typ, refClazzName: "java.lang." + typ.refClazzName } as ReturnedType}
                    name={name}
                    initialValue={initialValue}
                    inputLabel={userDefinedListInputLabel}
                />
            )}
        </>
    );
}
