import React, { useCallback, useContext } from "react";
import { AdhocTestingFormContext } from "./AdhocTestingFormContext";
import { ExpressionLang, ExpressionObj } from "../../graph/node-modal/editors/expression/types";
import { spelFormatters } from "../../graph/node-modal/editors/expression/Formatter";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { FormControl } from "@mui/material";
import { ParamFieldLabel } from "../../graph/node-modal/FieldLabel";
import { nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { getValidationErrorsForField } from "../../graph/node-modal/editors/Validators";
import { FieldSwitch } from "../../graph/node-modal/editors/field/FieldSwitch";
import { editors } from "../../graph/node-modal/editors/expression/Editor";

export function FormField({ name }: { name: string }) {
    const { value, setValue, variableTypes, parameters = [], errors } = useContext(AdhocTestingFormContext);

    const setParam = useCallback(
        (name: string) => (value: ExpressionObj | string) => {
            if (typeof value === "string") {
                return setValue((current) => ({
                    ...current,
                    [name]: {
                        ...current[name],
                        expression: value,
                    },
                }));
            }
            setValue((current) => ({
                ...current,
                [name]: value,
            }));
        },
        [setValue],
    );

    const parameter = parameters.find((p) => p.name === name);

    if (!parameter) {
        return null;
    }

    const { defaultValue, typ, editors: availableEditors } = parameter;

    const formatter = defaultValue.language === ExpressionLang.SpEL ? spelFormatters[typ?.refClazzName] : null;
    return (
        <NodeTable sx={{ m: 0 }}>
            <FormControl>
                <ParamFieldLabel parameterDefinitions={parameters} paramName={name} />
                <FieldSwitch
                    availableEditors={availableEditors}
                    expressionObj={value[name]}
                    onValueChange={setParam(name)}
                    readOnly={false}
                    showSwitch={true}
                >
                    {(selectedEditor) => {
                        const Editor = editors[selectedEditor.type];

                        return (
                            <Editor
                                editorConfig={selectedEditor}
                                className={nodeValue}
                                fieldErrors={getValidationErrorsForField(errors, name)}
                                formatter={formatter}
                                expressionInfo={null}
                                onValueChange={setParam(name)}
                                expressionObj={value[name]}
                                readOnly={false}
                                key={name}
                                showSwitch={true}
                                showValidation={true}
                                variableTypes={variableTypes}
                            />
                        );
                    }}
                </FieldSwitch>
            </FormControl>
        </NodeTable>
    );
}
