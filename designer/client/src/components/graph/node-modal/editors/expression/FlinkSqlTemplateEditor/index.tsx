import { Alert, Box, FormControl, MenuItem, Select, TextField } from "@mui/material";
import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { getBorderColor } from "../../../../../../containers/theme/helpers";
import type { VariableTypes } from "../../../../../../types/validation";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import type { FieldError } from "../../Validators";
import type { OnValueChange } from "../Editor";
import type { ExpressionObj } from "../types";
import { CepEditor } from "./cep/CepEditor";
import { FormRow, LABEL_FLEX_BASIS } from "./components/FormRow";
import { nuMenuProps, nuSelectSx } from "./components/nuInputSx";
import { SqlPreviewRow } from "./components/SqlPreviewRow";
import { DedupEditor } from "./dedup/DedupEditor";
import { WindowDedupEditor } from "./dedup/WindowDedupEditor";
import { InputFieldsSection } from "./InputFieldsSection";
import { generateGenericBoilerplate, generateSql } from "./sqlGenerator";
import { parseSql, TEMPLATE_MARKER_PREFIX } from "./sqlParser";
import type { ParsedResult } from "./sqlParser";
import { WindowTopNEditor } from "./topn/WindowTopNEditor";
import type { InputField, TemplateType, VisualEditorState } from "./types";
function getIncompleteReasons(state: VisualEditorState, t: ReturnType<typeof import("react-i18next").useTranslation>["t"]): string[] {
    if (state.template.type === "generic") return [];
    if (!state.inputFields.some((f) => f.selected)) {
        return [t("flinkSql.incomplete.selectVariable", "Select an input variable to generate SQL.")];
    }
    switch (state.template.type) {
        case "cep":
            if (state.template.config.measures.length === 0)
                return [t("flinkSql.incomplete.noMeasures", "Add at least one measure to generate valid SQL.")];
            break;
        case "dedup":
            if (state.template.config.partitionBy.length === 0)
                return [t("flinkSql.incomplete.noPartitionKey", "Select at least one partition key to generate valid SQL.")];
            break;
        case "windowTopN":
            if (!state.template.config.orderBy) return [t("flinkSql.incomplete.noOrderBy", "Select an order field to generate valid SQL.")];
            break;
    }
    return [];
}
import {
    buildInputFieldsForVariable,
    defaultCepState,
    defaultDedupState,
    defaultWindowDedupState,
    defaultWindowTopNState,
    EXCLUDED_INPUT_VARIABLES,
} from "./types";

interface Props {
    expressionObj: ExpressionObj;
    onValueChange: OnValueChange;
    readOnly?: boolean;
    variableTypes: VariableTypes;
    SqlEditorComponent: React.ReactNode;
    outputVar?: string;
    onOutputVarChange?: (name: string) => void;
    fieldErrors?: FieldError[];
    outputVarErrors?: FieldError[];
    showValidation?: boolean;
}

const TEMPLATES: { type: TemplateType; label: string }[] = [
    { type: "generic", label: "Generic" },
    { type: "cep", label: "Match Recognize" },
    { type: "dedup", label: "Deduplication" },
    { type: "windowDedup", label: "Window Deduplication" },
    { type: "windowTopN", label: "Window Top-N" },
];

function buildAllFieldsFromVariableTypes(variableTypes: VariableTypes): InputField[] {
    const fields: InputField[] = [];
    for (const [varName] of Object.entries(variableTypes)) {
        if (EXCLUDED_INPUT_VARIABLES.has(varName)) continue;
        fields.push(...buildInputFieldsForVariable(varName, variableTypes));
    }
    return fields;
}

const TEMPLATE_MARKERS: Partial<Record<TemplateType, string>> = {
    cep: `${TEMPLATE_MARKER_PREFIX}cep`,
    dedup: `${TEMPLATE_MARKER_PREFIX}dedup`,
    windowDedup: `${TEMPLATE_MARKER_PREFIX}windowDedup`,
    windowTopN: `${TEMPLATE_MARKER_PREFIX}windowTopN`,
};

function withMarker(sql: string, type: TemplateType): string {
    const marker = TEMPLATE_MARKERS[type];
    return marker ? `${marker}\n${sql}` : sql;
}

function initialParsed(expressionObj: ExpressionObj, variableTypes: VariableTypes): ParsedResult {
    const availableFields = buildAllFieldsFromVariableTypes(variableTypes).map((f) => ({ ...f, selected: false }));
    const sql = expressionObj.expression?.trim() ?? "";

    // Allow marker-prefixed SQL through; skip other comment-only SQL (e.g. "-- Select input fields first")
    if (sql && (sql.startsWith(TEMPLATE_MARKER_PREFIX) || !sql.startsWith("--"))) {
        const result = parseSql(sql, availableFields);
        if (result) return result;
    }

    // No SQL, unrecognized, or no marker → generic template
    return { state: { inputFields: [], template: { type: "generic" } } };
}

export function FlinkSqlTemplateEditor({
    expressionObj,
    onValueChange,
    readOnly,
    variableTypes,
    SqlEditorComponent,
    outputVar,
    onOutputVarChange,
    fieldErrors,
    outputVarErrors,
    showValidation,
}: Props) {
    const { t } = useTranslation();

    // Lazy ref initialization: runs initialParsed only once (on first render).
    // useRef(null) + conditional assignment is the recommended pattern for expensive
    // one-time initialization that must not re-run on re-renders (unlike useMemo which
    // React may discard in concurrent mode).
    const initialResultRef = useRef<ParsedResult | null>(null);
    if (initialResultRef.current === null) initialResultRef.current = initialParsed(expressionObj, variableTypes);
    const [{ state, warning }, setResult] = useState<ParsedResult>(initialResultRef.current);
    const resultRef = useRef<ParsedResult>(initialResultRef.current);

    const onValueChangeRef = useRef(onValueChange);
    const expressionObjRef = useRef(expressionObj);
    // Keep refs in sync after every render (useLayoutEffect avoids the theoretical
    // concurrent-mode race where a callback fires between render and commit).
    useLayoutEffect(() => {
        onValueChangeRef.current = onValueChange;
        expressionObjRef.current = expressionObj;
    });

    const applyResult = useCallback((next: ParsedResult) => {
        resultRef.current = next;
        setResult(next);
    }, []);

    // variableTypes may arrive asynchronously (Unknown on first render, proper types after compilation).
    // If the initial parse had no available fields (inputFields empty), re-parse once types become available.
    const availableFieldCount = buildAllFieldsFromVariableTypes(variableTypes).length;
    useEffect(() => {
        if (availableFieldCount === 0) return;
        if (resultRef.current.state.inputFields.length > 0) return;
        const availableFields = buildAllFieldsFromVariableTypes(variableTypes).map((f) => ({ ...f, selected: false }));
        const sql = expressionObjRef.current.expression?.trim() ?? "";
        if (!sql || (sql.startsWith("--") && !sql.startsWith(TEMPLATE_MARKER_PREFIX))) return;
        const result = parseSql(sql, availableFields);
        if (result) applyResult(result);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [availableFieldCount]);

    const setVisualState = useCallback(
        (updater: (s: VisualEditorState) => VisualEditorState) => {
            const prev = resultRef.current;
            const next = { ...prev, state: updater(prev.state) };
            applyResult(next);
            if (next.state.template.type !== "generic") {
                onValueChangeRef.current({
                    ...expressionObjRef.current,
                    expression: withMarker(generateSql(next.state), next.state.template.type),
                });
            }
        },
        [applyResult],
    );

    const handleFieldsChange = useCallback(
        (fields: InputField[]) => {
            const prev = resultRef.current;
            const next = { ...prev, state: { ...prev.state, inputFields: fields } };
            applyResult(next);
            if (next.state.template.type === "generic") {
                const boilerplate = generateGenericBoilerplate(fields);
                if (boilerplate) onValueChangeRef.current({ ...expressionObjRef.current, expression: boilerplate });
            } else {
                onValueChangeRef.current({
                    ...expressionObjRef.current,
                    expression: withMarker(generateSql(next.state), next.state.template.type),
                });
            }
        },
        [applyResult],
    );

    const handleTemplateChange = useCallback(
        (tpl: TemplateType) => {
            if (!tpl) return;
            const currentSql = expressionObjRef.current.expression?.trim() ?? "";
            const availableFields = buildAllFieldsFromVariableTypes(variableTypes).map((f) => ({ ...f, selected: false }));
            const prev = resultRef.current;

            if (tpl === "generic") {
                applyResult({ ...prev, warning: undefined, state: { ...prev.state, template: { type: "generic" } } });
                if (prev.state.template.type !== "generic") {
                    const currentExpr = expressionObjRef.current.expression ?? "";
                    const stripped = currentExpr.replace(/^--@nu:\w+\n/, "");
                    if (stripped !== currentExpr) {
                        onValueChangeRef.current({ ...expressionObjRef.current, expression: stripped });
                    }
                }
                return;
            }

            const parsed = currentSql ? parseSql(currentSql, availableFields) : null;
            if (parsed && parsed.state.template.type === tpl) {
                applyResult({ ...parsed, state: { ...parsed.state, inputFields: prev.state.inputFields } });
                return;
            }

            const template =
                tpl === "cep"
                    ? { type: "cep" as const, config: defaultCepState() }
                    : tpl === "dedup"
                    ? { type: "dedup" as const, config: defaultDedupState() }
                    : tpl === "windowDedup"
                    ? { type: "windowDedup" as const, config: defaultWindowDedupState() }
                    : { type: "windowTopN" as const, config: defaultWindowTopNState() };
            const nextState: VisualEditorState = { ...prev.state, template };
            applyResult({ warning: undefined, state: nextState });
            onValueChangeRef.current({
                ...expressionObjRef.current,
                expression: withMarker(generateSql(nextState), tpl),
            });
        },
        [variableTypes, applyResult],
    );

    const activeTemplate = state.template.type;
    const previewSql = activeTemplate !== "generic" ? generateSql(state) : null;
    const incompleteReasons = getIncompleteReasons(state, t);

    return (
        <Box display="flex" flexDirection="column">
            {warning && (
                <Alert severity="warning" sx={{ fontSize: "0.78rem", py: 0.5 }}>
                    {t("flinkSql.parseWarning", "SQL could not be fully parsed: {{warning}} — some fields may be incomplete.", { warning })}
                </Alert>
            )}

            {/* Query type: select */}
            <FormRow label={t("flinkSql.queryType", "Query type:")} alignItems="center">
                <FormControl size="small" variant="outlined" fullWidth>
                    <Select
                        value={activeTemplate}
                        onChange={(e) => handleTemplateChange(e.target.value as TemplateType)}
                        disabled={readOnly}
                        MenuProps={nuMenuProps}
                        sx={nuSelectSx}
                    >
                        {TEMPLATES.map((tpl) => (
                            <MenuItem key={tpl.type} value={tpl.type} sx={{ fontSize: "0.85rem" }}>
                                {tpl.label}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            </FormRow>

            {/* Source variable — shared across all templates */}
            <InputFieldsSection
                fields={state.inputFields}
                onChange={handleFieldsChange}
                readOnly={readOnly}
                variableTypes={variableTypes}
            />

            {/* Generic: raw SQL editor */}
            {activeTemplate === "generic" && <Box p={0}>{SqlEditorComponent}</Box>}

            {/* Visual templates */}
            {state.template.type === "cep" && (
                <CepEditor
                    fields={state.inputFields}
                    config={state.template.config}
                    onChange={(config) => setVisualState((s) => ({ ...s, template: { type: "cep", config } }))}
                    readOnly={readOnly}
                    outputVar={outputVar}
                    onOutputVarChange={onOutputVarChange}
                />
            )}
            {state.template.type === "dedup" && (
                <DedupEditor
                    fields={state.inputFields}
                    config={state.template.config}
                    onChange={(config) => setVisualState((s) => ({ ...s, template: { type: "dedup", config } }))}
                    readOnly={readOnly}
                />
            )}
            {state.template.type === "windowDedup" && (
                <WindowDedupEditor
                    fields={state.inputFields}
                    config={state.template.config}
                    onChange={(config) => setVisualState((s) => ({ ...s, template: { type: "windowDedup", config } }))}
                    readOnly={readOnly}
                />
            )}
            {state.template.type === "windowTopN" && (
                <WindowTopNEditor
                    fields={state.inputFields}
                    config={state.template.config}
                    onChange={(config) => setVisualState((s) => ({ ...s, template: { type: "windowTopN", config } }))}
                    readOnly={readOnly}
                />
            )}

            {/* Output variable name — shown for all non-CEP templates (CEP has it inside CepEditor) */}
            {activeTemplate !== "cep" && onOutputVarChange && (
                <FormRow label={t("flinkSql.outputVar", "Output variable:")} alignItems="flex-start">
                    <Box>
                        <TextField
                            size="small"
                            value={outputVar ?? ""}
                            onChange={(e) => onOutputVarChange(e.target.value)}
                            disabled={readOnly}
                            fullWidth
                            sx={(theme) => ({
                                "& .MuiOutlinedInput-root": {
                                    borderRadius: 0,
                                    backgroundColor: "background.paper",
                                    fontSize: "0.85rem",
                                    height: 35,
                                    outline: `1px solid ${getBorderColor(theme)}`,
                                    "&:focus-within": { outline: `1px solid ${theme.palette.primary.main}` },
                                    "& fieldset": { border: "none" },
                                },
                            })}
                        />
                        {showValidation && outputVarErrors && outputVarErrors.length > 0 && (
                            <ValidationLabels fieldErrors={outputVarErrors} />
                        )}
                    </Box>
                </FormRow>
            )}

            {incompleteReasons.length > 0 ? (
                <Alert severity="info" sx={{ fontSize: "0.78rem", py: 0.5 }}>
                    {incompleteReasons[0]}
                </Alert>
            ) : (
                <>
                    {/* Generated SQL preview — all visual templates */}
                    {previewSql !== null && <SqlPreviewRow sql={previewSql} />}

                    {/* Validation errors — visual templates only (generic shows them inside the Ace editor) */}
                    {showValidation && activeTemplate !== "generic" && fieldErrors && fieldErrors.length > 0 && (
                        <Box sx={{ pl: LABEL_FLEX_BASIS }}>
                            <ValidationLabels fieldErrors={fieldErrors} />
                        </Box>
                    )}
                </>
            )}
        </Box>
    );
}
