import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";

import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeValidationError } from "../../../types/validation";
import Field, { FieldType } from "./editors/field/Field";
import { FieldLabelConsumer } from "./editors/RenderFieldLabel";
import { extendErrors, getValidationErrorsForField, mandatoryValueValidator, uniqueScenarioValueValidator } from "./editors/Validators";
import { nodeInput, nodeInputWithError } from "./NodeDetailsContent/NodeTableStyled";
import type { EditedNode } from "./nodeIdFieldHelpers";
import {
    cleanNodeIdPlaceholder,
    FAKE_NAME_PROP_NAME,
    fixNodeIdValue,
    getCurrentEditedId,
    getProcessNodesIds,
    PLACEHOLDER_CHARACTER,
    PROP_NAME,
} from "./nodeIdFieldHelpers";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface IdFieldProps {
    isEditMode?: boolean;
    node: EditedNode;

    setProperty?: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
}

export function IdField({ isEditMode, node, setProperty, showValidation, errors }: IdFieldProps): React.JSX.Element {
    const nodes = useAppSelector(getProcessNodesIds);
    // stable node id before edits
    const [otherNodes] = useState(() => nodes.filter((n) => n !== node[PROP_NAME]));

    const [isMarked] = useDiffMark();
    const value = useMemo(() => getCurrentEditedId(node), [node]);
    const marked = useMemo(() => isMarked(FAKE_NAME_PROP_NAME) || isMarked(PROP_NAME), [isMarked]);

    const extraValidators = useMemo(() => {
        return [uniqueScenarioValueValidator(otherNodes), mandatoryValueValidator];
    }, [otherNodes]);

    const [internalValue, setInternalValue] = useState(value);

    const fieldErrors = useMemo(
        () => getValidationErrorsForField(extendErrors(errors, internalValue, FAKE_NAME_PROP_NAME, extraValidators), FAKE_NAME_PROP_NAME),
        [errors, extraValidators, internalValue],
    );

    useEffect(() => {
        setInternalValue(value.replace(PLACEHOLDER_CHARACTER, ""));
    }, [value]);

    const onChange = useCallback(
        (newValue: string) => {
            setInternalValue(cleanNodeIdPlaceholder(newValue));
            const fixedValue = fixNodeIdValue(newValue, extraValidators);
            setProperty(FAKE_NAME_PROP_NAME, fixedValue);
        },
        [extraValidators, setProperty],
    );

    return (
        <Field
            type={FieldType.input}
            isMarked={marked}
            showValidation={showValidation}
            onChange={onChange}
            readOnly={!isEditMode}
            className={!showValidation || isEmpty(fieldErrors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`}
            fieldErrors={fieldErrors}
            value={internalValue}
            autoFocus
        >
            <FieldLabelConsumer text="Name" />
        </Field>
    );
}
