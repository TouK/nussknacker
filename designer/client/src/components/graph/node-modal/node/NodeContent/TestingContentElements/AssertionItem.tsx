import { css } from "@emotion/css";
import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import { Box, IconButton } from "@mui/material";
import React, { useCallback, useMemo, useState, memo } from "react";
import { useTranslation } from "react-i18next";

import type { AssertionOperator } from "../../../../../../actions/nk/testCasesActions";
import { useUserSettings } from "../../../../../../common/useUserSettings";
import type { TestAssertionResult } from "../../../../../../http/resultsWithCountsDto";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../../../../types/validation";
import type { ContextData } from "../../../../../dataMapper/DataMapper";
import { SpelExpressionBuilderComponent } from "../../../../../spelExpressionBuilder/SpelExpressionBuilderComponent";
import { RowFieldLabel } from "../../../aggregate/rowFieldLabel";
import { EditableEditor } from "../../../editors/EditableEditor";
import type { ExpressionObj } from "../../../editors/expression/types";
import { EditorType, ExpressionLang } from "../../../editors/expression/types";
import { UseRecordsPrefixContext, UseValueInsertContext } from "../../../editors/expression/useAceDndTarget";
import Input from "../../../editors/field/Input";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { ParamKeyProvider } from "../../../editors/ParamKeyProvider";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import { TypeSelect } from "../../../fragment-input-definition/TypeSelect";
import type { Option } from "../../../fragment-input-definition/TypeSelect";
import { getNodesDetails } from "../../../NodeDetailsContent/getNodeDetails";
import { OverrideKeys } from "../../../parameterHelpers";
import { AssertionStatus } from "./AssertionStatus";

const RECORDS_HINT_KEY = "editors.spelEditor.additionalInfoText.assertionActual";
const RECORDS_HINT_DEFAULT =
    "Use **#records** to access all events that passed through this node.\n\ncount `#records.size()`  \nfield access `#records[0].status`  \nfilter `#records.?[#this.amount > 100].size()`";

export const ASSERTION_SYMBOLS: Record<AssertionOperator, string> = {
    equals: "==",
    notEquals: "!=",
    greaterThan: ">",
    lessThan: "<",
    greaterThanOrEqual: ">=",
    lessThanOrEqual: "<=",
    hasSize: "has size",
    contains: "contains",
    matches: "matches",
};

const gridContainerStyle = css({
    "&&&&": {
        width: "100%",
        display: "grid",
        gridTemplateColumns: "1fr 1fr minmax(7rem, max-content) 1fr",
        gridTemplateRows: "auto auto",
        gridTemplateAreas: `"field field field field remove" "expr expr expr expr remove"`,
    },
});

interface Props {
    uuid: string;
    description?: string;
    expected: ExpressionObj;
    operator: AssertionOperator;
    actual: ExpressionObj;
    node: NodeType;
    variableTypes: VariableTypes;
    contextData?: ContextData;
    onChange: (
        uuid: string,
        updated: Partial<{ description: string; expected: ExpressionObj; operator: AssertionOperator; actual: ExpressionObj }>,
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
    node,
    contextData,
    onChange,
    index,
    testAssertionResult,
    variableTypes,
    errors = [],
}: Props) => {
    const { t } = useTranslation();
    const isFirstRow = index === 0;
    const [showFieldExpressionBuilder] = useUserSettings("node.showAssertionExpressionBuilder");
    const [builderOpen, setBuilderOpen] = useState(false);

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
            onChange(uuid, { operator: value as AssertionOperator });
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

    const isValidating = useAppSelector((state) => getNodesDetails(state)[node.id]?.isValidating ?? false);
    const recordsHint = t(RECORDS_HINT_KEY, RECORDS_HINT_DEFAULT);

    const handleOpenBuilder = useCallback(() => setBuilderOpen(true), []);
    const handleCloseBuilder = useCallback(() => setBuilderOpen(false), []);
    const handleBuilderInsert = useCallback((spel: string) => handleActualChange({ expression: spel }), [handleActualChange]);

    const actualAdornment = showFieldExpressionBuilder ? (
        <InfoTooltip variant="hover" title={recordsHint}>
            <IconButton onClick={handleOpenBuilder} color="primary" sx={{ padding: 0 }}>
                <AutoFixHighIcon sx={{ width: "1rem", height: "1rem" }} />
            </IconButton>
        </InfoTooltip>
    ) : undefined;

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
                <RowFieldLabel showLabel={isFirstRow} label="Actual" data-testid={`assertion-actual-${index}`}>
                    <UseRecordsPrefixContext.Provider value={true}>
                        <ParamKeyProvider custom={OverrideKeys.AssertionActual}>
                            <EditableEditor
                                showSwitch={false}
                                editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                                expressionObj={actual}
                                variableTypes={variableTypes}
                                onValueChange={handleActualChange}
                                showValidation
                                isValidating={isValidating}
                                fieldErrors={actualErrors}
                                placeholder="#records.size()"
                                inputAdornmentEnd={actualAdornment}
                            />
                        </ParamKeyProvider>
                    </UseRecordsPrefixContext.Provider>
                </RowFieldLabel>
                <RowFieldLabel showLabel={isFirstRow} label="Assertion" data-testid={`assertion-operator-${index}`}>
                    <TypeSelect
                        value={assertionOptions.find((o) => o.value === operator)}
                        options={assertionOptions}
                        onChange={handleOperatorChange}
                    />
                </RowFieldLabel>
                <RowFieldLabel showLabel={isFirstRow} label="Expected" data-testid={`assertion-expected-${index}`}>
                    <UseValueInsertContext.Provider value={true}>
                        <EditableEditor
                            showSwitch={false}
                            editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                            expressionObj={expected}
                            variableTypes={variableTypes}
                            onValueChange={handleExpectedChange}
                            showValidation
                            isValidating={isValidating}
                            fieldErrors={expectedErrors}
                            placeholder="true, 12, 'xxxx'"
                        />
                    </UseValueInsertContext.Provider>
                </RowFieldLabel>
            </FieldsRow>
            {showFieldExpressionBuilder && (
                <SpelExpressionBuilderComponent
                    node={node}
                    variableTypes={variableTypes}
                    contextData={contextData}
                    initialExpression={actual.expression}
                    onInsert={handleBuilderInsert}
                    open={builderOpen}
                    onClose={handleCloseBuilder}
                />
            )}
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
