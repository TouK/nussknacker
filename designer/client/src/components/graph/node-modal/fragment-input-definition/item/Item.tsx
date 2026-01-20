import { isEqual } from "lodash";
import type { PropsWithChildren } from "react";
import React, { useCallback } from "react";

import type { ReturnedType } from "../../../../../types/scenarioGraph";
import type { NodeValidationError, VariableTypes } from "../../../../../types/validation";
import Input from "../../editors/field/Input";
import { getValidationErrorsForField } from "../../editors/Validators";
import { useFieldsControl } from "../../node-row-fields-provider/FieldsControl";
import { NodeValue } from "../../node/NodeValue";
import { useDiffMark } from "../../PathsToMark";
import SettingsButton from "../buttons/SettingsButton";
import { FieldsRow } from "../FieldsRow";
import type { Option } from "../FieldsSelect";
import { Settings } from "../settings/Settings";
import { SettingsProvider } from "../settings/SettingsProvider";
import { TypeSelect } from "../TypeSelect";
import type { FragmentInputParameter, onChangeType } from "./types";
import { resolveRefClazzName } from "./utils";

type CommonItemProps = {
    index: number;
    item: FragmentInputParameter;
    namespace: string;
    readOnly?: boolean;
    onChange: (path: string, value: onChangeType) => void;
    errors: NodeValidationError[];
};

type ItemSettingsProps = CommonItemProps & {
    variableTypes: VariableTypes;
};

function ItemSettings({ index, item, namespace, variableTypes, readOnly, onChange, errors }: ItemSettingsProps) {
    const path = `${namespace}[${index}]`;

    return (
        <SettingsProvider
            initialFixedValuesList={item?.valueEditor?.fixedValuesList}
            initialTemporaryValueCompileTimeValidation={item?.valueCompileTimeValidation}
        >
            <Settings
                path={path}
                item={item}
                onChange={onChange}
                variableTypes={variableTypes}
                readOnly={readOnly}
                errors={errors}
                data-testid={`settings:${index}`}
            />
        </SettingsProvider>
    );
}

type ItemFieldsProps = CommonItemProps & {
    showValidation?: boolean;
    options: Option[];
};

function ItemFields({
    children,
    index,
    item,
    namespace,
    readOnly,
    showValidation,
    onChange,
    options,
    errors,
}: PropsWithChildren<ItemFieldsProps>) {
    const path = `${namespace}[${index}]`;
    const [isMarked] = useDiffMark();
    const getCurrentOption = useCallback(
        (typ: ReturnedType | undefined) => {
            const fallbackValue = { label: resolveRefClazzName(typ?.refClazzName), value: resolveRefClazzName(typ?.refClazzName) };
            const foundValue = options.find((item) => isEqual(typ?.refClazzName, item.value));
            return foundValue || fallbackValue;
        },
        [options],
    );

    return (
        <FieldsRow index={index} uuid={item.uuid}>
            <NodeValue>
                <Input
                    readOnly={readOnly}
                    showValidation={showValidation}
                    isMarked={isMarked(`${path}.name`)}
                    onChange={(e) => onChange(`${path}.name`, e.target.value)}
                    value={item.name}
                    placeholder="Field name"
                    fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$name`)}
                />
            </NodeValue>
            <TypeSelect
                readOnly={readOnly}
                onChange={(value) => {
                    onChange(`${path}.typ.refClazzName`, value);
                    onChange(`${path}.valueEditor`, null);
                }}
                value={getCurrentOption(item.typ)}
                isMarked={isMarked(`${path}.typ.refClazzName`)}
                options={options}
                fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$typ`)}
            />
            {children}
        </FieldsRow>
    );
}

export function Item(props: ItemSettingsProps & ItemFieldsProps) {
    const { isExpanded, toggleExpanded } = useFieldsControl();
    return (
        <div>
            <ItemFields {...props}>
                <SettingsButton isOpen={isExpanded(props.item.uuid)} toggleIsOpen={() => toggleExpanded(props.item.uuid)} />
            </ItemFields>
            {isExpanded(props.item.uuid) ? <ItemSettings {...props} /> : null}
        </div>
    );
}
