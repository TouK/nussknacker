import React, { PropsWithChildren, useCallback } from "react";
import { Field, NodeType, NodeValidationError } from "../../../types";
import LabeledInput from "./editors/field/LabeledInput";
import { useDiffMark } from "./PathsToMark";
import { getValidationErrorsForField } from "./editors/Validators";
import { IdField } from "./IdField";
import { DescriptionField } from "./DescriptionField";

export interface NodeDetailsProps<F extends Field> {
    node: NodeType<F>;
    setProperty?: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
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
            <IdField
                node={node}
                isEditMode={!readOnly}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
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

            <DescriptionField
                isEditMode={!readOnly}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
