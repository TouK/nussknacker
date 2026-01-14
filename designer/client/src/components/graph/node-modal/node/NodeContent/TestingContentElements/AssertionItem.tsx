import { css, cx } from "@emotion/css";
import { Box } from "@mui/material";
import React from "react";

import type { TestAssertionResult } from "../../../../../../http/resultsWithCountsDto";
import type { VariableTypes } from "../../../../../../types/validation";
import { NonDraggableLabel } from "../../../aggregate/dynamicLabel";
import { EditableEditor } from "../../../editors/EditableEditor";
import type { ExpressionObj } from "../../../editors/expression/types";
import { EditorType } from "../../../editors/expression/types";
import Input from "../../../editors/field/Input";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import { AssertionStatus } from "./AssertionStatus";

interface Props {
    uuid: string;
    expressionObj: ExpressionObj;
    assertionVariableTypes: VariableTypes;
    onChange: (uuid: string, expression: { expression: ExpressionObj }) => void;
    testAssertionResult: TestAssertionResult | undefined;
    index: number;
}
export const AssertionItem = ({ uuid, expressionObj, assertionVariableTypes, onChange, index, testAssertionResult }: Props) => {
    const isFirstRow = index === 0;

    return (
        <Box display={"flex"} alignItems={"flex-start"}>
            <FieldsRow
                key={uuid}
                index={index}
                uuid={uuid}
                className={cx(
                    css({
                        "&&&&": {
                            display: "grid",
                            gridTemplateColumns: "3fr 1fr 3fr auto",
                            gridTemplateRows: "auto auto",
                            gridTemplateAreas: `"field field field remove" "expr expr expr x"`,
                        },
                    }),
                )}
            >
                <NonDraggableLabel hovered={isFirstRow} label="Expected">
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={expressionObj}
                        variableTypes={assertionVariableTypes}
                        onValueChange={(expression) => onChange(uuid, { expression })}
                        fieldErrors={[]}
                    />
                </NonDraggableLabel>
                <NonDraggableLabel hovered={isFirstRow} label="Assertion">
                    <Input value={"assertEquals"} disabled={true} fieldErrors={[]} />
                </NonDraggableLabel>
                <NonDraggableLabel hovered={isFirstRow} label="Actual">
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={expressionObj}
                        variableTypes={assertionVariableTypes}
                        onValueChange={(expression) => onChange(uuid, { expression })}
                        fieldErrors={[]}
                    />
                </NonDraggableLabel>
            </FieldsRow>
            {testAssertionResult && (
                <Box ml={1} mt={0.5}>
                    <AssertionStatus
                        status={testAssertionResult.type === "SuccessfulAssertion" ? "success" : "error"}
                        message={testAssertionResult.type === "FailedAssertion" ? testAssertionResult.message : undefined}
                    />
                </Box>
            )}
        </Box>
    );
};
