import React, { useEffect, useMemo, useState } from "react";
import Field, { FieldType } from "./editors/field/Field";
import { FieldError, getValidationErrorForField } from "./editors/Validators";
import { NodeType, NodeValidationError, ProcessDefinitionData } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import { useDiffMark } from "./PathsToMark";
import { useTranslation } from "react-i18next";
import { NodeRow } from "./NodeDetailsContent/NodeStyled";
import { NodeLabelStyled } from "./node";

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
    fieldError: FieldError;
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
    fieldError,
}: OutputFieldProps): JSX.Element {
    const readOnly = !isEditMode || readonly;

    const className = !showValidation || !fieldError ? "node-input" : "node-input node-input-with-error";
    const [isMarked] = useDiffMark();

    return (
        <Field
            type={fieldType}
            isMarked={isMarked(`${fieldProperty}`)}
            readOnly={readOnly}
            showValidation={showValidation}
            autoFocus={autoFocus}
            className={className}
            fieldError={fieldError}
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

    const typeDefinition = useMemo(
        () => ProcessUtils.findNodeObjectTypeDefinition(editedNode, processDefinitionData.processDefinition),
        [editedNode, processDefinitionData.processDefinition],
    );
    const isDefinitionAvailable = !!typeDefinition.outputParameters && isEditMode;

    const [variableNames, setVariableNames] = useState<Record<string, string>>(() => {
        if (!isDefinitionAvailable) {
            return currentVariableNames;
        }

        const entries = typeDefinition.outputParameters.map((value) => [value, currentVariableNames?.[value]]);
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
        typeDefinition.outputParameters
            ?.filter((paramName) => variableNames[paramName] === undefined)
            .forEach((paramName) => {
                setVariableNames((prevState) => ({
                    ...prevState,
                    [paramName]: paramName,
                }));
            });
    }, [typeDefinition.outputParameters, variableNames]);

    const entries = Object.entries(variableNames);

    if (entries.length <= 0) {
        return null;
    }

    return (
        <NodeRow key="outputVariableNames">
            <NodeLabelStyled title={t("parameterOutputs.outputsTitle", "Fragment outputs names")}>
                {t("parameterOutputs.outputsText", "Outputs names:")}
            </NodeLabelStyled>
            <div className="node-value">
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
                            fieldError={getValidationErrorForField(errors, `${outputVariablePath}.${name}`)}
                        />
                    ))}
                </div>
            </div>
        </NodeRow>
    );
}
