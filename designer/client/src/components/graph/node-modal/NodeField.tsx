import { cx } from "@emotion/css";
import { get, isEmpty } from "lodash";
import React, { useCallback, useMemo } from "react";

import type { NodeOrPropertiesType, NodeValidationError } from "../../../types";
import type { FieldType } from "./editors/field/Field";
import Field from "./editors/field/Field";
import { getValidationErrorsForField } from "./editors/Validators";
import { nodeInput, nodeInputWithError } from "./NodeDetailsContent/NodeTableStyled";
import type { SetProperty } from "./NodeTypeDetailsContent";
import { useDiffMark } from "./PathsToMark";

type NodeFieldProps<N extends string, V> = {
    autoFocus?: boolean;
    defaultValue?: V;
    fieldLabel?: string;
    fieldName: N;
    fieldType: FieldType;
    isEditMode?: boolean;
    node: NodeOrPropertiesType;
    readonly?: boolean;
    renderFieldLabel: (paramName: string) => React.ReactNode;
    setProperty: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
    description?: string;
};

export function NodeField<N extends string, V>({
    autoFocus,
    defaultValue,
    fieldLabel,
    fieldName,
    fieldType,
    isEditMode,
    node,
    readonly,
    renderFieldLabel,
    setProperty,
    showValidation,
    errors,
    description,
}: NodeFieldProps<N, V>): JSX.Element {
    const readOnly = !isEditMode || readonly;
    const value = useMemo(() => get(node, fieldName, null) ?? defaultValue, [defaultValue, fieldName, node]);
    const fieldErrors = useMemo(() => getValidationErrorsForField(errors, fieldName), [errors, fieldName]);

    const className = useMemo(
        () => cx({ [nodeInput]: true, [nodeInputWithError]: showValidation && !isEmpty(fieldErrors) }),
        [fieldErrors, showValidation],
    );
    const onChange = useCallback((newValue) => setProperty(fieldName, newValue, defaultValue), [defaultValue, fieldName, setProperty]);
    const [isMarked] = useDiffMark();

    return (
        <Field
            type={fieldType}
            isMarked={isMarked(fieldName)}
            readOnly={readOnly}
            showValidation={showValidation}
            autoFocus={autoFocus}
            className={className}
            fieldErrors={fieldErrors}
            value={value}
            onChange={onChange}
            description={description}
        >
            {renderFieldLabel(fieldLabel)}
        </Field>
    );
}
