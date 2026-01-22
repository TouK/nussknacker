import React, { useCallback, useContext } from "react";

import { EditorByType } from "../../graph/node-modal/editors/expression/EditorByType";
import { spelFormatters } from "../../graph/node-modal/editors/expression/Formatter";
import type { ExpressionObj } from "../../graph/node-modal/editors/expression/types";
import { ExpressionLang } from "../../graph/node-modal/editors/expression/types";
import { FieldSwitch } from "../../graph/node-modal/editors/field/FieldSwitch";
import { FormControl } from "../../graph/node-modal/editors/FormControl";
import { getValidationErrorsForField } from "../../graph/node-modal/editors/Validators";
import { ParamFieldLabel } from "../../graph/node-modal/FieldLabel";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { AdhocTestingFormContext } from "./AdhocTestingFormContext";

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
                    {(type, editorConfig) => (
                        <EditorByType
                            type={type}
                            config={editorConfig}
                            className={nodeValue}
                            fieldErrors={getValidationErrorsForField(errors, name)}
                            formatter={formatter}
                            onValueChange={setParam(name)}
                            expressionObj={value[name]}
                            readOnly={false}
                            key={name}
                            showSwitch={true}
                            showValidation={true}
                            variableTypes={variableTypes}
                        />
                    )}
                </FieldSwitch>
            </FormControl>
        </NodeTable>
    );
}
