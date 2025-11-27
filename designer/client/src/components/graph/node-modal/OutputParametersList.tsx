import { isEmpty } from "lodash";
import type { PropsWithChildren } from "react";
import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import ProcessUtils from "../../../common/ProcessUtils";
import type { NodeType } from "../../../types/node";
import type { ComponentDefinition, ProcessDefinitionData } from "../../../types/scenarioGraph";
import type { NodeValidationError } from "../../../types/validation";
import Field, { FieldType } from "./editors/field/Field";
import { FieldLabelConsumer } from "./editors/RenderFieldLabel";
import type { FieldError } from "./editors/Validators";
import { getValidationErrorsForField } from "./editors/Validators";
import { NodeRow } from "./node/NodeRow";
import { nodeInput, nodeInputWithError, nodeValue } from "./NodeDetailsContent/NodeTableStyled";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

type OutputFieldProps = PropsWithChildren<{
    autoFocus?: boolean;
    value: string;
    fieldProperty: string;
    fieldType: FieldType;
    isEditMode?: boolean;
    readonly?: boolean;
    onChange: (value: string) => void;
    showValidation?: boolean;
    fieldErrors: FieldError[];
}>;

function OutputField({
    autoFocus,
    value,
    fieldProperty,
    fieldType,
    isEditMode,
    readonly,
    onChange,
    showValidation,
    fieldErrors,
    children,
}: OutputFieldProps): React.JSX.Element {
    const readOnly = !isEditMode || readonly;

    const className = !showValidation || isEmpty(fieldErrors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`;
    const [isMarked] = useDiffMark();

    return (
        <Field
            type={fieldType}
            isMarked={isMarked(`${fieldProperty}`)}
            readOnly={readOnly}
            showValidation={showValidation}
            autoFocus={autoFocus}
            className={className}
            fieldErrors={fieldErrors}
            value={value}
            onChange={onChange}
        >
            {children}
        </Field>
    );
}

const outputVariablePath = "ref.outputVariableNames";

export default function OutputParametersList({
    editedNode,
    processDefinitionData,
    errors,
    isEditMode,
    showValidation,

    setProperty,
}: {
    editedNode: NodeType;
    processDefinitionData: ProcessDefinitionData;

    setProperty: SetProperty;
    errors?: NodeValidationError[];
    showValidation?: boolean;
    isEditMode?: boolean;
}): React.JSX.Element {
    const currentVariableNames = editedNode.ref?.outputVariableNames;

    const componentDefinition: ComponentDefinition = useMemo(
        () => ProcessUtils.extractComponentDefinition(editedNode, processDefinitionData.components),
        [editedNode, processDefinitionData.components],
    );
    const isDefinitionAvailable = !!componentDefinition?.outputParameters && isEditMode;

    const [variableNames, setVariableNames] = useState<Record<string, string>>(() => {
        if (!isDefinitionAvailable) {
            return currentVariableNames;
        }

        const entries = componentDefinition.outputParameters.map((value) => [value, currentVariableNames?.[value]]);
        return Object.fromEntries(entries);
    });

    const { t } = useTranslation();

    useEffect(() => {
        if (!isDefinitionAvailable) {
            return;
        }
        setProperty(outputVariablePath, variableNames);
    }, [variableNames, setProperty, isDefinitionAvailable]);

    useEffect(() => {
        componentDefinition?.outputParameters
            ?.filter((paramName) => variableNames[paramName] === undefined)
            .forEach((paramName) => {
                setVariableNames((prevState) => ({
                    ...prevState,
                    [paramName]: paramName,
                }));
            });
    }, [componentDefinition?.outputParameters, variableNames]);

    const entries = Object.entries(variableNames);

    if (entries.length <= 0) {
        return null;
    }

    return (
        <NodeRow
            key="outputVariableNames"
            label={t("parameterOutputs.outputsText", "Outputs names:")}
            title={t("parameterOutputs.outputsTitle", "Fragment outputs names")}
        >
            <div className={nodeValue}>
                <div className="fieldsControl">
                    {entries.map(([name, value]) => (
                        <OutputField
                            key={name}
                            isEditMode={isEditMode}
                            showValidation={showValidation}
                            value={value === undefined ? name : value}
                            onChange={(value) =>
                                setVariableNames((prevState) => ({
                                    ...prevState,
                                    [name]: value,
                                }))
                            }
                            fieldType={FieldType.input}
                            fieldProperty={name}
                            fieldErrors={getValidationErrorsForField(errors, `${outputVariablePath}.${name}`)}
                        >
                            <FieldLabelConsumer text={name} />
                        </OutputField>
                    ))}
                </div>
            </div>
        </NodeRow>
    );
}
