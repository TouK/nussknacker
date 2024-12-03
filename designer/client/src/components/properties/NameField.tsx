import React, { ComponentProps } from "react";
import Field, { FieldType } from "../graph/node-modal/editors/field/Field";
import { isEmpty } from "lodash";
import { nodeInput, nodeInputWithError } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import { NodeValidationError } from "../../types";
import { PropertiesForm } from "./PropertiesForm";
import { useDiffMark } from "../graph/node-modal/PathsToMark";

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
