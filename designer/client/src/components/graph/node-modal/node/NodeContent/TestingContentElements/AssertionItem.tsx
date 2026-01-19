import { css } from "@emotion/css";
import { Box } from "@mui/material";
import React, { useCallback, useMemo, memo } from "react";

import type { TestAssertionResult } from "../../../../../../http/resultsWithCountsDto";
import type { NodeValidationError, VariableTypes } from "../../../../../../types/validation";
import { NonDraggableLabel } from "../../../aggregate/dynamicLabel";
import { EditableEditor } from "../../../editors/EditableEditor";
import type { ExpressionObj } from "../../../editors/expression/types";
import { EditorType, ExpressionLang } from "../../../editors/expression/types";
import Input from "../../../editors/field/Input";
import { EMPTY_REQUIRED_ERROR } from "../../../editors/Validators";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import type { AssertionParts } from "./assertionEncoder";
import { encodeAssertionExpression, decodeAssertionExpression } from "./assertionEncoder";
import { AssertionStatus } from "./AssertionStatus";

const ASSERTION_SYMBOLS: Record<string, string> = {
    assertEquals: "==",
};

const centeredInputStyle = css({
    "& input": {
        textAlign: "center",
    },
});

const gridContainerStyle = css({
    "&&&&": {
        display: "grid",
        gridTemplateColumns: "4fr 1fr 4fr",
        gridTemplateRows: "auto auto",
        gridTemplateAreas: `"field field field remove" "expr expr expr remove"`,
    },
});

interface Props {
    uuid: string;
    expressionObj: ExpressionObj;
    variableTypes: VariableTypes;
    onChange: (uuid: string, expression: { expression: ExpressionObj }) => void;
    testAssertionResult: TestAssertionResult | undefined;
    index: number;
    errors: NodeValidationError[];
}

const AssertionItemComponent = ({ uuid, expressionObj, onChange, index, testAssertionResult, variableTypes, errors = [] }: Props) => {
    const isFirstRow = index === 0;

    const decodedParts = useMemo(() => {
        return decodeAssertionExpression(expressionObj.expression);
    }, [expressionObj.expression]);

    const expectedExpressionObj: ExpressionObj = useMemo(() => {
        const expectedValue = decodedParts?.expected ?? "";
        return {
            expression: expectedValue,
            language: ExpressionLang.SpEL,
        };
    }, [decodedParts?.expected]);

    const actualExpressionObj: ExpressionObj = useMemo(() => {
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

    const handleExpectedChange = useCallback(
        ({ expression }) => handleChangeAssertionPart("expected", expression),
        [handleChangeAssertionPart],
    );

    const handleActualChange = useCallback(
        ({ expression }) => handleChangeAssertionPart("actual", expression),
        [handleChangeAssertionPart],
    );

    const assertionSymbol = useMemo(() => {
        return ASSERTION_SYMBOLS[decodedParts?.assertion] ?? "";
    }, [decodedParts?.assertion]);

    return (
        <Box display={"flex"} alignItems={"flex-start"}>
            <FieldsRow key={uuid} index={index} uuid={uuid} className={gridContainerStyle}>
                <NonDraggableLabel hovered={isFirstRow} label="Expected">
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={expectedExpressionObj}
                        variableTypes={variableTypes}
                        onValueChange={handleExpectedChange}
                        showValidation
                        fieldErrors={errors}
                    />
                </NonDraggableLabel>
                <NonDraggableLabel hovered={isFirstRow} label="Assertion">
                    <Input value={assertionSymbol} disabled={true} className={centeredInputStyle} />
                </NonDraggableLabel>
                <NonDraggableLabel hovered={isFirstRow} label="Actual">
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={actualExpressionObj}
                        variableTypes={variableTypes}
                        onValueChange={handleActualChange}
                        showValidation
                        fieldErrors={errors.length > 0 ? [EMPTY_REQUIRED_ERROR] : []}
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

export const AssertionItem = memo(AssertionItemComponent);
