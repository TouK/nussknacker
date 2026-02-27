import { css } from "@emotion/css";
import { Box } from "@mui/material";
import React, { useCallback, useMemo, memo } from "react";

import type { TestAssertionResult } from "../../../../../../http/resultsWithCountsDto";
import type { NodeValidationError, VariableTypes } from "../../../../../../types/validation";
import { RowFieldLabel } from "../../../aggregate/rowFieldLabel";
import { EditableEditor } from "../../../editors/EditableEditor";
import type { ExpressionObj } from "../../../editors/expression/types";
import { EditorType, ExpressionLang } from "../../../editors/expression/types";
import Input from "../../../editors/field/Input";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import { TypeSelect } from "../../../fragment-input-definition/TypeSelect";
import type { Option } from "../../../fragment-input-definition/TypeSelect";
import { AssertionStatus } from "./AssertionStatus";

export const ASSERTION_SYMBOLS: Record<string, string> = {
    equals: "==",
    notEquals: "!=",
};

const gridContainerStyle = css({
    "&&&&": {
        width: "100%",
        display: "grid",
        gridTemplateColumns: "3fr 3fr 1fr 3fr",
        gridTemplateRows: "auto auto",
        gridTemplateAreas: `"field field field field remove" "expr expr expr expr remove"`,
    },
});

interface Props {
    uuid: string;
    description?: string;
    expected: ExpressionObj;
    operator: "equals" | "notEquals";
    actual: ExpressionObj;
    variableTypes: VariableTypes;
    onChange: (
        uuid: string,
        updated: Partial<{ description: string; expected: ExpressionObj; operator: "equals" | "notEquals"; actual: ExpressionObj }>,
    ) => void;
    testAssertionResult: TestAssertionResult | undefined;
    index: number;
    errors: NodeValidationError[];
}

const AssertionItemComponent = ({
    uuid,
    description,
    expected,
    operator,
    actual,
    onChange,
    index,
    testAssertionResult,
    variableTypes,
    errors = [],
}: Props) => {
    const isFirstRow = index === 0;

    const handleDescriptionChange = useCallback(
        (event) => {
            onChange(uuid, { description: event.target.value });
        },
        [onChange, uuid],
    );

    const handleExpectedChange = useCallback(
        ({ expression }: { expression: string }) => {
            onChange(uuid, { expected: { expression, language: ExpressionLang.SpEL } });
        },
        [onChange, uuid],
    );

    const handleActualChange = useCallback(
        ({ expression }: { expression: string }) => {
            onChange(uuid, { actual: { expression, language: ExpressionLang.SpEL } });
        },
        [onChange, uuid],
    );

    const assertionOptions: Option[] = useMemo(() => Object.entries(ASSERTION_SYMBOLS).map(([value, label]) => ({ value, label })), []);

    const handleOperatorChange = useCallback(
        (value: string) => {
            onChange(uuid, { operator: value as "equals" | "notEquals" });
        },
        [onChange, uuid],
    );

    const expectedErrors = useMemo(() => {
        return errors.filter((error) => error.fieldName === "expected") || [];
    }, [errors]);

    const actualErrors = useMemo(() => {
        return errors.filter((error) => error.fieldName === "actual") || [];
    }, [errors]);

    const descriptionErrors = useMemo(() => {
        return errors.filter((error) => error.fieldName === "description") || [];
    }, [errors]);

    return (
        <Box display={"flex"} alignItems={"flex-start"} data-assertion-uuid={uuid}>
            <FieldsRow key={uuid} index={index} uuid={uuid} className={gridContainerStyle}>
                <RowFieldLabel showLabel={isFirstRow} label="Description" data-testid={`assertion-description-${index}`}>
                    <Input
                        value={description}
                        onChange={handleDescriptionChange}
                        fieldErrors={descriptionErrors}
                        showValidation
                        placeholder={"Optional description"}
                    />
                </RowFieldLabel>
                <RowFieldLabel showLabel={isFirstRow} label="Expected" data-testid={`assertion-expected-${index}`}>
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={expected}
                        variableTypes={variableTypes}
                        onValueChange={handleExpectedChange}
                        showValidation
                        fieldErrors={expectedErrors}
                    />
                </RowFieldLabel>
                <RowFieldLabel showLabel={isFirstRow} label="Assertion" data-testid={`assertion-operator-${index}`}>
                    <TypeSelect
                        value={assertionOptions.find((o) => o.value === operator)}
                        options={assertionOptions}
                        onChange={handleOperatorChange}
                    />
                </RowFieldLabel>
                <RowFieldLabel showLabel={isFirstRow} label="Actual" data-testid={`assertion-actual-${index}`}>
                    <EditableEditor
                        showSwitch={false}
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={actual}
                        variableTypes={variableTypes}
                        onValueChange={handleActualChange}
                        showValidation
                        fieldErrors={actualErrors}
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
