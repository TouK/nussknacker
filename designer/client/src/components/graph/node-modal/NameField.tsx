import { isEmpty } from "lodash";
import React, { useCallback, useMemo } from "react";

import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import Field, { FieldType } from "./editors/field/Field";
import { FieldLabelConsumer } from "./editors/RenderFieldLabel";
import { getValidationErrorsForField } from "./editors/Validators";
import { nodeInput, nodeInputWithError } from "./NodeDetailsContent/NodeTableStyled";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

const FIELD_NAME = "$name";

interface NameFieldProps {
    isEditMode?: boolean;
    node: NodeType;
    setProperty?: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
}

export function NameField({ isEditMode, node, setProperty, showValidation, errors }: NameFieldProps): React.JSX.Element {
    const [isMarked] = useDiffMark();
    const marked = useMemo(() => isMarked(FIELD_NAME), [isMarked]);

    const fieldErrors = useMemo(() => getValidationErrorsForField(errors, FIELD_NAME), [errors]);

    const onChange = useCallback(
        (newValue: string) => {
            setProperty("name", newValue);
        },
        [setProperty],
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
            value={node.name}
            autoFocus
        >
            <FieldLabelConsumer text="Name" />
        </Field>
    );
}
