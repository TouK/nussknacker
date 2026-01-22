import { css } from "@emotion/css";
import { Box } from "@mui/material";
import React, { useCallback, useMemo, memo, useState, useRef } from "react";
import { useMutationObserver } from "rooks";

import type { TestAssertionResult } from "../../../../../../http/resultsWithCountsDto";
import type { NodeValidationError, VariableTypes } from "../../../../../../types/validation";
import { RowFieldLabel } from "../../../aggregate/rowFieldLabel";
import { EditableEditor } from "../../../editors/EditableEditor";
import type { ExpressionObj } from "../../../editors/expression/types";
import { EditorType, ExpressionLang } from "../../../editors/expression/types";
import Input from "../../../editors/field/Input";
import { EMPTY_REQUIRED_ERROR } from "../../../editors/Validators";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import type { AssertionParts } from "./assertionEncoder";
import { decodeAssertionExpression, encodeAssertionExpression } from "./assertionEncoder";
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
    const [hasExpectedError, setHasExpectedError] = useState(false);
    const bodyRef = useRef(document.body);

    const mutationObserverOptions = useMemo(
        () => ({
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ["class"],
        }),
        [],
    );

    const checkForExpectedError = useCallback(() => {
        const assertionContainer = document.querySelector(`[data-assertion-uuid="${uuid}"]`);
        const expectedLabel = assertionContainer?.querySelector('[label="Expected"]');
        const hasMuiError = expectedLabel?.querySelector(".Mui-error") !== null;
        setHasExpectedError(hasMuiError);
    }, [uuid]);

    //TODO: remove this logic when backend ready to validate specific assertion field
    useMutationObserver(bodyRef, checkForExpectedError, mutationObserverOptions);

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
        <Box display={"flex"} alignItems={"flex-start"} data-assertion-uuid={uuid}>
            <FieldsRow key={uuid} index={index} uuid={uuid} className={gridContainerStyle}>
                <RowFieldLabel showLabel={isFirstRow} label="Expected">
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={expectedExpressionObj}
                        variableTypes={variableTypes}
                        onValueChange={handleExpectedChange}
                        showValidation
                        fieldErrors={errors}
                    />
                </RowFieldLabel>
                <RowFieldLabel showLabel={isFirstRow} label="Assertion">
                    <Input value={assertionSymbol} disabled={true} className={centeredInputStyle} />
                </RowFieldLabel>
                <RowFieldLabel showLabel={isFirstRow} label="Actual">
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={actualExpressionObj}
                        variableTypes={variableTypes}
                        onValueChange={handleActualChange}
                        showValidation
                        fieldErrors={errors.length > 0 && hasExpectedError ? [EMPTY_REQUIRED_ERROR] : []}
                    />
                </RowFieldLabel>
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
