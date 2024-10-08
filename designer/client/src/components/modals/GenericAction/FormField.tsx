import React, { useCallback, useContext } from "react";
import { GenericActionFormContext } from "./GenericActionFormContext";
import { editors, ExtendedEditor, SimpleEditor } from "../../graph/node-modal/editors/expression/Editor";
import {ExpressionLang, ExpressionObj} from "../../graph/node-modal/editors/expression/types";
import { spelFormatters } from "../../graph/node-modal/editors/expression/Formatter";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { FormControl } from "@mui/material";
import { ParamFieldLabel } from "../../graph/node-modal/FieldLabel";
import ErrorBoundary from "../../common/ErrorBoundary";
import { nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { getValidationErrorsForField } from "../../graph/node-modal/editors/Validators";

export function FormField({ name }: { name: string }) {
    const { value, setValue, variableTypes, parameters = [], errors } = useContext(GenericActionFormContext);

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

    const { defaultValue, editor, typ } = parameter;
    const Editor: SimpleEditor | ExtendedEditor = editors[editor.type];
    const formatter = defaultValue.language === ExpressionLang.SpEL ? spelFormatters[typ?.refClazzName] : null;
    return (
        <NodeTable sx={{ m: 0 }}>
            <FormControl>
                <ParamFieldLabel parameterDefinitions={parameters} paramName={name} />
                <ErrorBoundary>
                    <Editor
                        editorConfig={editor}
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
                </ErrorBoundary>
            </FormControl>
        </NodeTable>
    );
}
