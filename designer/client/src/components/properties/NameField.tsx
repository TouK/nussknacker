import { isEmpty } from "lodash";
import type { ComponentProps } from "react";
import React from "react";

import type { NodeValidationError } from "../../types/validation";
import Field, { FieldType } from "../graph/node-modal/editors/field/Field";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import { nodeInput, nodeInputWithError } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { useDiffMark } from "../graph/node-modal/PathsToMark";
import type { PropertiesForm } from "./PropertiesForm";

const FAKE_NAME_PROP_NAME = "$id";
const fieldName = "name";

interface Props {
    readOnly: boolean;
    errors: NodeValidationError[];
    value: string;
    onChange: ComponentProps<typeof PropertiesForm>["handleSetEditedProperties"];
}
export const NameField = ({ readOnly, errors, value, onChange }: Props) => {
    const [isMarked] = useDiffMark();
    return (
        <Field
            type={FieldType.input}
            isMarked={isMarked(fieldName)}
            showValidation
            onChange={(newValue) => onChange(fieldName, newValue.toString())}
            readOnly={readOnly}
            className={isEmpty(errors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`}
            // TODO: we need to change this fieldName on the backend from $id to name
            fieldErrors={getValidationErrorsForField(errors, FAKE_NAME_PROP_NAME)}
            value={value}
            autoFocus
        >
            <FieldLabel title={"Name"} label={"Name"} />
        </Field>
    );
};
