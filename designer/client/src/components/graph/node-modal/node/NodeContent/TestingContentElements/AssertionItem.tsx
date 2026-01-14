import { css, cx } from "@emotion/css";
import { Box } from "@mui/material";
import React, { useCallback, useMemo } from "react";

import type { TestAssertionResult } from "../../../../../../http/resultsWithCountsDto";
import type { VariableTypes } from "../../../../../../types/validation";
import { NonDraggableLabel } from "../../../aggregate/dynamicLabel";
import { EditableEditor } from "../../../editors/EditableEditor";
import type { ExpressionObj } from "../../../editors/expression/types";
import { EditorType, ExpressionLang } from "../../../editors/expression/types";
import Input from "../../../editors/field/Input";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import type { AssertionParts } from "./assertionEncoder";
import { encodeAssertionExpression, decodeAssertionExpression } from "./assertionEncoder";
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

    const decodedParts = useMemo(() => {
        return decodeAssertionExpression(expressionObj.expression);
    }, [expressionObj.expression]);

    const expectedExpressionObj = useMemo(() => {
        const expectedValue = decodedParts?.expected ?? "";
        return {
            expression: expectedValue,
            language: ExpressionLang.SpEL,
        };
    }, [decodedParts?.expected]);

    const actualExpressionObj = useMemo(() => {
        const actualValue = decodedParts?.actual ?? "";
        return {
            expression: actualValue,
            language: ExpressionLang.SpEL,
        };
    }, [decodedParts?.actual]);

    const handleChangeAssertionPart = useCallback(
        (part: keyof AssertionParts, value: string) => {
            const currentParts = decodedParts || {
                assertion: "assertEquals",
                expected: "",
                actual: "",
            };

            const updatedParts: AssertionParts = {
                ...currentParts,
                [part]: value,
            };

            const encodedExpression = encodeAssertionExpression(updatedParts);

            onChange(uuid, { expression: { language: ExpressionLang.SpEL, expression: encodedExpression } });
        },
        [decodedParts, onChange, uuid],
    );

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
                        expressionObj={expectedExpressionObj}
                        variableTypes={assertionVariableTypes}
                        onValueChange={({ expression }) => handleChangeAssertionPart("expected", expression)}
                        fieldErrors={[]}
                    />
                </NonDraggableLabel>
                <NonDraggableLabel hovered={isFirstRow} label="Assertion">
                    <Input
                        value={"assertEquals"}
                        disabled={true}
                        fieldErrors={[]}
                        onChange={(event) => handleChangeAssertionPart("assertion", event.target.value)}
                    />
                </NonDraggableLabel>
                <NonDraggableLabel hovered={isFirstRow} label="Actual">
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={actualExpressionObj}
                        variableTypes={assertionVariableTypes}
                        onValueChange={({ expression }) => handleChangeAssertionPart("actual", expression)}
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
