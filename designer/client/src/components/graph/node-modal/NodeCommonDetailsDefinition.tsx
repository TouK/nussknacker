import React, { PropsWithChildren, useCallback } from "react";
import { Field, NodeType, NodeValidationError } from "../../../types";
import LabeledInput from "./editors/field/LabeledInput";
import LabeledTextarea from "./editors/field/LabeledTextarea";
import { useDiffMark } from "./PathsToMark";
import { getValidationErrorsForField } from "./editors/Validators";

export interface NodeDetailsProps<F extends Field> {
    node: NodeType<F>;
    setProperty: (propToMutate: string, newValue: unknown) => void;
    readOnly?: boolean;
    showValidation: boolean;
    renderFieldLabel: (label: string) => React.ReactNode;
    errors: NodeValidationError[];
}

interface NodeCommonDetailsDefinitionProps<F extends Field> extends PropsWithChildren<NodeDetailsProps<F>> {
    outputName?: string;
    outputField?: string;
}

export function NodeCommonDetailsDefinition<F extends Field>({ children, ...props }: NodeCommonDetailsDefinitionProps<F>): JSX.Element {
    const { node, setProperty, readOnly, showValidation, renderFieldLabel, errors, outputField, outputName } = props;

    const onInputChange = useCallback(
        (path: string, event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
            setProperty(path, event.target.value);
        },
        [setProperty],
    );

    const [isMarked] = useDiffMark();

    return (
        <>
            <LabeledInput
                value={node.id}
                onChange={(event) => onInputChange("id", event)}
                isMarked={isMarked("id")}
                readOnly={readOnly}
                showValidation={showValidation}
                fieldErrors={getValidationErrorsForField(errors, "$id")}
            >
                {renderFieldLabel("Name")}
            </LabeledInput>
            {outputField && outputName && (
                <LabeledInput
                    value={node[outputField]}
                    onChange={(event) => onInputChange(outputField, event)}
                    isMarked={isMarked(outputField)}
                    readOnly={readOnly}
                    showValidation={showValidation}
                    fieldErrors={getValidationErrorsForField(errors, outputField)}
                >
                    {renderFieldLabel(outputName)}
                </LabeledInput>
            )}

            {children}

            <LabeledTextarea
                value={node.additionalFields?.description || ""}
                onChange={(event) => onInputChange("additionalFields.description", event)}
                isMarked={isMarked("additionalFields.description")}
                readOnly={readOnly}
                className={"node-input"}
                fieldErrors={getValidationErrorsForField(errors, "additionalFields.description")}
            >
                {renderFieldLabel("Description")}
            </LabeledTextarea>
        </>
    );
}
