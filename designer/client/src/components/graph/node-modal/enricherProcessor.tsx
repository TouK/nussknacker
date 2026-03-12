import { Box, Dialog, DialogContent } from "@mui/material";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import {
    CONDITION_BUILDER_ENRICHER_KINDS,
    DATA_MAPPER_OPENAPI_ENRICHER_KINDS,
    determineComponentId,
    getComponentKind,
} from "../../../common/componentUtils";
import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import { typingResultToSample } from "../../builderComponents/typeUtils";
import { ConditionBuilder } from "../../conditionBuilder/ConditionBuilder";
import type { ContextData } from "../../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../../dataMapper/DataMapperDialogTitle";
import { DescriptionField } from "./DescriptionField";
import { DisableField } from "./DisableField";
import type { TableData } from "./editors/expression/Table/state/tableState";
import { EditorType, ExpressionLang } from "./editors/expression/types";
import { FieldType } from "./editors/field/Field";
import { IdField } from "./IdField";
import { useInputOutputContext } from "./io/InputOutputContext";
import { NamedParamsDataMapper } from "./NamedParamsDataMapper";
import { StyledLoadingButton } from "./node-action-buttons/StyledLoadingButton";
import { findParameters } from "./NodeDetailsContent/helpers";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";
import { NodeField } from "./NodeField";
import { ParametersListWithOverrides } from "./ParametersListWithOverrides";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

/** Parse the first row of a decision-table TableData into a plain object with typed values. */
function firstRowToContext(tableData: TableData): Record<string, unknown> | undefined {
    const { columns, rows } = tableData;
    if (!columns.length || !rows.length) return undefined;
    const first = rows[0];
    const NUMERIC_TYPES = new Set(["java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal"]);
    return Object.fromEntries(
        columns.map((col, i) => {
            const raw = first[i] ?? "";
            let val: unknown = raw;
            if (NUMERIC_TYPES.has(col.type)) {
                const n = Number(raw);
                val = isNaN(n) ? raw : n;
            } else if (col.type === "java.lang.Boolean") {
                val = raw === "true" ? true : raw === "false" ? false : raw;
            }
            return [col.name, val];
        }),
    );
}

export function EnricherProcessor({
    errors,
    isEditMode,
    node,
    parameterDefinitions,
    setProperty,
    showSwitch,
    showValidation,
}: {
    errors: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}): React.JSX.Element {
    const { t } = useTranslation();
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const kind = getComponentKind(determineComponentId(node));

    // ── Decision-table ConditionBuilder ──────────────────────────────────────
    const [conditionBuilderOpen, setConditionBuilderOpen] = useState(false);
    const variableTypes = useMemo(() => findAvailableVariables(node.id), [findAvailableVariables, node.id]);
    const ioContext = useInputOutputContext();

    const booleanParam = useMemo(
        () =>
            parameterDefinitions.find(
                (p) =>
                    !p.changesCanReloadParameters &&
                    p.editors?.some((e) => e.type === EditorType.SPEL_PARAMETER_EDITOR) &&
                    p.typ?.refClazzName === "java.lang.Boolean",
            ),
        [parameterDefinitions],
    );

    const mergedVariableTypes = useMemo<VariableTypes>(() => {
        if (!booleanParam?.additionalVariables) return variableTypes ?? {};
        return { ...(variableTypes ?? {}), ...booleanParam.additionalVariables };
    }, [variableTypes, booleanParam]);

    const currentParams = useMemo(() => findParameters(node), [node]);

    const tableFirstRow = useMemo<Record<string, unknown> | undefined>(() => {
        const tableParam = currentParams.find((p) => p.expression?.language === ExpressionLang.TabularDataDefinition);
        if (!tableParam?.expression?.expression) return undefined;
        try {
            const tableData: TableData = JSON.parse(tableParam.expression.expression);
            return firstRowToContext(tableData);
        } catch {
            return undefined;
        }
    }, [currentParams]);

    const conditionBuilderContextData = useMemo<ContextData | undefined>(() => {
        const [contexts] = ioContext?.getAvailableContexts("input") ?? [[]];
        const selected = ioContext?.state.inputDataSetId ? contexts.find((c) => c.id === ioContext.state.inputDataSetId) : undefined;
        const base: ContextData = contexts.length
            ? Object.fromEntries(
                  Object.keys((selected ?? contexts[0]).variables).map((k) => [k, (selected ?? contexts[0]).variables[k].pretty]),
              )
            : {};
        if (booleanParam?.additionalVariables) {
            Object.entries(booleanParam.additionalVariables).forEach(([k, v]) => {
                base[k] = tableFirstRow ?? typingResultToSample(v);
            });
        }
        return Object.keys(base).length > 0 ? base : undefined;
    }, [ioContext, booleanParam, tableFirstRow]);

    const initialExpression = useMemo(() => {
        if (!booleanParam) return undefined;
        const current = currentParams.find((p) => p.name === booleanParam.name);
        return current?.expression?.language === ExpressionLang.SpEL ? current.expression.expression : undefined;
    }, [currentParams, booleanParam]);

    const handleConditionInsert = useCallback(
        (spel: string) => {
            if (!booleanParam) return;
            const paramIndex = currentParams.findIndex((p) => p.name === booleanParam.name);
            if (paramIndex >= 0) {
                setProperty(`service.parameters[${paramIndex}].expression`, { expression: spel, language: ExpressionLang.SpEL });
            }
            setConditionBuilderOpen(false);
        },
        [booleanParam, currentParams, setProperty],
    );
    // ─────────────────────────────────────────────────────────────────────────

    return (
        <>
            <IdField isEditMode={isEditMode} showValidation={showValidation} node={node} setProperty={setProperty} errors={errors} />
            <ParametersListWithOverrides
                parameters={findParameters(node)}
                isEditMode={isEditMode}
                showValidation={showValidation}
                showSwitch={showSwitch}
                node={node}
                findAvailableVariables={findAvailableVariables}
                parameterDefinitions={parameterDefinitions}
                errors={errors}
                setProperty={setProperty}
                getListFieldPath={(index: number) => `service.parameters[${index}]`}
            >
                {node.type === "Enricher" && isEditMode && kind && DATA_MAPPER_OPENAPI_ENRICHER_KINDS.has(kind) && (
                    <NamedParamsDataMapper node={node} parameterDefinitions={parameterDefinitions} setProperty={setProperty} />
                )}
                {node.type === "Enricher" && isEditMode && kind && CONDITION_BUILDER_ENRICHER_KINDS.has(kind) && booleanParam && (
                    <>
                        <Box display="flex" flexDirection="column" alignItems="flex-end" width="100%">
                            <StyledLoadingButton title="Condition Builder" action={() => setConditionBuilderOpen(true)} />
                        </Box>
                        {conditionBuilderOpen && (
                            <Dialog open onClose={() => setConditionBuilderOpen(false)} maxWidth="xl" fullWidth>
                                <DataMapperDialogTitle
                                    node={node}
                                    onClose={() => setConditionBuilderOpen(false)}
                                    title="condition builder"
                                />
                                <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                                    <ConditionBuilder
                                        onInsert={handleConditionInsert}
                                        variableTypes={mergedVariableTypes}
                                        contextData={conditionBuilderContextData}
                                        initialExpression={initialExpression}
                                        highlightKeys={
                                            booleanParam?.additionalVariables
                                                ? new Set(Object.keys(booleanParam.additionalVariables))
                                                : undefined
                                        }
                                    />
                                </DialogContent>
                            </Dialog>
                        )}
                    </>
                )}
                {node.type === "Enricher" ? (
                    <NodeField
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        node={node}
                        setProperty={setProperty}
                        fieldType={FieldType.input}
                        fieldLabel={t("nodes.enricher.output", "Output variable name")}
                        fieldName={"output"}
                        errors={errors}
                    />
                ) : null}
                {node.type === "Processor" ? (
                    <DisableField
                        node={node}
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        setProperty={setProperty}
                        errors={errors}
                    />
                ) : null}
                <DescriptionField
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    setProperty={setProperty}
                    errors={errors}
                />
            </ParametersListWithOverrides>
        </>
    );
}
