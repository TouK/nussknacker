import { styled } from "@mui/material";
import React from "react";

import type { TestAssertionResult } from "../../../../../../http/resultsWithCountsDto";
import type { VariableTypes } from "../../../../../../types/validation";
import { EditableEditor } from "../../../editors/EditableEditor";
import type { ExpressionObj } from "../../../editors/expression/types";
import { EditorType } from "../../../editors/expression/types";

const WithLabel = styled("div")(({ theme }) => ({
    width: "100%",
    "&:first-of-type::before": {
        ...theme.typography.overline,
        color: theme.palette.text.disabled,
        content: "'Expected'",
        position: "absolute",
        bottom: "100%",
        marginBottom: theme.spacing(0.75),
    },
}));

interface Props {
    uuid: string;
    expressionObj: ExpressionObj;
    assertionVariableTypes: VariableTypes;
    onChange: (uuid: string, expression: { expression: ExpressionObj }) => void;
    testAssertionResult: TestAssertionResult | undefined;
}
export const AssertionItem = ({ uuid, expressionObj, assertionVariableTypes, onChange, testAssertionResult }: Props) => {
    return (
        <>
            <WithLabel>
                <EditableEditor
                    showSwitch={false}
                    editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                    expressionObj={expressionObj}
                    variableTypes={assertionVariableTypes}
                    onValueChange={(expression) => onChange(uuid, { expression })}
                    fieldErrors={[]}
                />
            </WithLabel>
            <WithLabel>
                <EditableEditor
                    showSwitch={false}
                    editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                    expressionObj={expressionObj}
                    variableTypes={assertionVariableTypes}
                    onValueChange={(expression) => onChange(uuid, { expression })}
                    fieldErrors={[]}
                />
            </WithLabel>
            <WithLabel>
                <EditableEditor
                    showSwitch={false}
                    editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                    expressionObj={expressionObj}
                    variableTypes={assertionVariableTypes}
                    onValueChange={(expression) => onChange(uuid, { expression })}
                    fieldErrors={[]}
                />
            </WithLabel>
        </>
    );
};
