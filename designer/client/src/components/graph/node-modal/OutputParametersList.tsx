import React, { useEffect, useMemo, useState } from "react";
import Field, { FieldType } from "./editors/field/Field";
import { FieldError, getValidationErrorsForField } from "./editors/Validators";
import { ComponentDefinition, NodeType, NodeValidationError, ProcessDefinitionData } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import { useDiffMark } from "./PathsToMark";
import { useTranslation } from "react-i18next";
import { isEmpty } from "lodash";
import { FormControl, FormLabel } from "@mui/material";
import { nodeInput, nodeInputWithError, nodeValue } from "./NodeDetailsContent/NodeTableStyled";

type OutputFieldProps = {
    autoFocus?: boolean;
    value: string;
    fieldProperty: string;
    fieldType: FieldType;
    isEditMode?: boolean;
    readonly?: boolean;
    renderedFieldLabel: JSX.Element;
    onChange: (value: string) => void;
    showValidation?: boolean;
    fieldErrors: FieldError[];
};

function OutputField({
    autoFocus,
    value,
    fieldProperty,
    fieldType,
    isEditMode,
    readonly,
    renderedFieldLabel,
    onChange,
    showValidation,
    fieldErrors,
}: OutputFieldProps): JSX.Element {
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
            {renderedFieldLabel}
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
    renderFieldLabel,
    setProperty,
}: {
    editedNode: NodeType;
    processDefinitionData: ProcessDefinitionData;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    errors?: NodeValidationError[];
    showValidation?: boolean;
    isEditMode?: boolean;
}): JSX.Element {
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
        <FormControl key="outputVariableNames">
            <FormLabel title={t("parameterOutputs.outputsTitle", "Fragment outputs names")}>
                {t("parameterOutputs.outputsText", "Outputs names:")}
            </FormLabel>
            <div className={nodeValue}>
                <div className="fieldsControl">
                    {entries.map(([name, value]) => (
                        <OutputField
                            key={name}
                            isEditMode={isEditMode}
                            showValidation={showValidation}
                            value={value === undefined ? name : value}
                            renderedFieldLabel={renderFieldLabel(name)}
                            onChange={(value) =>
                                setVariableNames((prevState) => ({
                                    ...prevState,
                                    [name]: value,
                                }))
                            }
                            fieldType={FieldType.input}
                            fieldProperty={name}
                            fieldErrors={getValidationErrorsForField(errors, `${outputVariablePath}.${name}`)}
                        />
                    ))}
                </div>
            </div>
        </FormControl>
    );
}
