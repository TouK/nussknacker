import type { PropsWithChildren } from "react";
import React, { useCallback } from "react";

import type { Field, NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DescriptionField } from "./DescriptionField";
import LabeledInput from "./editors/field/LabeledInput";
import { FieldLabelConsumer } from "./editors/RenderFieldLabel";
import { getValidationErrorsForField } from "./editors/Validators";
import { IdField } from "./IdField";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

export interface NodeDetailsProps<F extends Field> {
    node: NodeType<F>;
    setProperty?: SetProperty;
    readOnly?: boolean;
    showValidation: boolean;

    errors: NodeValidationError[];
}

interface NodeCommonDetailsDefinitionProps<F extends Field> extends PropsWithChildren<NodeDetailsProps<F>> {
    outputName?: string;
    outputField?: string;
}

export function NodeCommonDetailsDefinition<F extends Field>({
    children,
    ...props
}: NodeCommonDetailsDefinitionProps<F>): React.JSX.Element {
    const { node, setProperty, readOnly, showValidation, errors, outputField, outputName } = props;

    const onInputChange = useCallback(
        (path: string, event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
            setProperty(path, event.target.value);
        },
        [setProperty],
    );

    const [isMarked] = useDiffMark();

    return (
        <>
            <IdField node={node} isEditMode={!readOnly} showValidation={showValidation} setProperty={setProperty} errors={errors} />
            {outputField && outputName && (
                <LabeledInput
                    value={node[outputField]}
                    onChange={(event) => onInputChange(outputField, event)}
                    isMarked={isMarked(outputField)}
                    readOnly={readOnly}
                    showValidation={showValidation}
                    fieldErrors={getValidationErrorsForField(errors, outputField)}
                >
                    <FieldLabelConsumer text={outputName} />
                </LabeledInput>
            )}

            {children}

            <DescriptionField
                isEditMode={!readOnly}
                showValidation={showValidation}
                node={node}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
