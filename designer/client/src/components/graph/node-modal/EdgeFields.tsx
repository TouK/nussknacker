import { Button, Dialog, DialogContent } from "@mui/material";
import { produce } from "immer";
import { isEqual, uniq } from "lodash";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { Edge } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeId } from "../../../types/node";
import type { VariableTypes } from "../../../types/validation";
import { ConditionBuilder } from "../../conditionBuilder/ConditionBuilder";
import type { ContextData } from "../../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../../dataMapper/DataMapperDialogTitle";
import NodeUtils from "../NodeUtils";
import type { EdgeTypeOption } from "./EdgeTypeSelect";
import { EdgeTypeSelect } from "./EdgeTypeSelect";
import { EditableEditor } from "./editors/EditableEditor";
import type { ExpressionObj } from "./editors/expression/types";
import { ExpressionLang } from "./editors/expression/types";
import type { FieldError } from "./editors/Validators";
import { FieldsRow } from "./fragment-input-definition/FieldsRow";
import type { Option } from "./fragment-input-definition/TypeSelect";
import { TypeSelect } from "./fragment-input-definition/TypeSelect";
import { useInputOutputContext } from "./io/InputOutputContext";

interface Props {
    edgeId: string;
    index: number;
    readOnly?: boolean;
    value: Edge;
    onChange: (edgeId: string, edge: Edge) => void;
    edges: Edge[];
    types?: EdgeTypeOption[];
    variableTypes?: VariableTypes;
    fieldErrors: FieldError[];
}

export function EdgeFields(props: Props): React.JSX.Element {
    const { t } = useTranslation();
    const { edgeId, readOnly, value: edge, index, onChange, edges, types, variableTypes, fieldErrors } = props;
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const processDefinitionData = useAppSelector(getProcessDefinitionData);
    const [conditionBuilderOpen, setConditionBuilderOpen] = useState(false);
    const setEdge = useCallback(
        (changes: (edge: Edge) => Edge) => {
            onChange(edgeId, changes(edge));
        },
        [edgeId, onChange, edge],
    );

    //NOTE: fragment node preview is read only so we can ignore wrong "process" and nodes here.
    const availableNodes = useMemo(() => scenarioGraph.nodes.filter((n) => NodeUtils.hasInputs(n)), [scenarioGraph.nodes]);
    const otherEdges = useMemo(() => scenarioGraph.edges.filter((e) => e.from !== edge.from), [edge.from, scenarioGraph.edges]);
    const targetNodes = useMemo(() => availableNodes.filter((n) => n.id === edge.to), [availableNodes, edge.to]);
    const freeNodes = useMemo(() => {
        return availableNodes.filter((n) => {
            return NodeUtils.canHaveMoreInputs(
                n,
                otherEdges.filter((e) => e.to === n.id),
                processDefinitionData,
            );
        });
    }, [availableNodes, otherEdges, processDefinitionData]);

    const nodeNameById = useMemo(
        () => Object.fromEntries(freeNodes.concat(targetNodes).map((n) => [n.id, n.name])),
        [freeNodes, targetNodes],
    );
    const targetOptions = useMemo<Option[]>(() => {
        const targets = uniq(freeNodes.concat(targetNodes).map((n) => n.id));
        return [
            { label: "⇢", value: "" },
            ...targets.map((nodeId) => ({
                label: `➝ ${nodeNameById[nodeId]}`,
                value: nodeId,
                isUsed: edges.some((e) => e.to === nodeId && !isEqual(e, edge)),
            })),
        ];
    }, [edge, edges, freeNodes, nodeNameById, targetNodes]);

    const onValueChange = useCallback(
        ({ expression, language }: ExpressionObj) => {
            setEdge(
                produce((draft) => {
                    // @ts-expect-error null
                    draft.edgeType.condition ||= {};
                    draft.edgeType.condition.expression = expression;
                    draft.edgeType.condition.language = language;
                }),
            );
        },
        [setEdge],
    );

    const onTargetChange = useCallback(
        (value: NodeId) => {
            setEdge(
                produce((draft) => {
                    draft.to = value;
                }),
            );
        },
        [setEdge],
    );

    const onTypeChange = useCallback(
        (type: EdgeKind) => {
            setEdge(
                produce((draft) => {
                    if (type === EdgeKind.switchNext) delete draft.edgeType.condition;
                    draft.edgeType.type = type;
                }),
            );
        },
        [setEdge],
    );

    const sourceNode = useMemo(() => scenarioGraph.nodes.find((n) => n.id === edge.from), [scenarioGraph.nodes, edge.from]);

    const ioContext = useInputOutputContext();
    const conditionBuilderContextData = useMemo<ContextData | undefined>(() => {
        const [contexts] = ioContext?.getAvailableContexts("input") ?? [[]];
        if (!contexts.length) return undefined;
        const selected = ioContext?.state.inputDataSetId ? contexts.find((c) => c.id === ioContext.state.inputDataSetId) : undefined;
        const vars = (selected ?? contexts[0]).variables;
        return Object.fromEntries(Object.keys(vars).map((k) => [k, vars[k].pretty]));
    }, [ioContext]);

    const conditionBuilderAdornment = useMemo(() => {
        if (readOnly || edge.edgeType?.type !== EdgeKind.switchNext) return undefined;
        return (
            <Button
                size="small"
                variant="contained"
                color="primary"
                onClick={() => setConditionBuilderOpen(true)}
                sx={{
                    minWidth: "unset",
                    padding: "1px 6px",
                    fontSize: 10,
                    lineHeight: 1.4,
                    textTransform: "none",
                    position: "relative",
                    zIndex: 1,
                }}
            >
                Build
            </Button>
        );
    }, [readOnly, edge.edgeType?.type]);

    const showType = useMemo(() => types.length > 1 || uniq(edges.map((e) => e.edgeType?.type)).length > 1, [edges, types.length]);
    return (
        <>
            <FieldsRow
                uuid={edge._id}
                index={index}
                sx={{
                    "&&&&": {
                        display: "grid",
                        gridTemplateColumns: "1fr 2fr auto",
                    },
                    "&&&&:has(.edge-value)": {
                        gridTemplateColumns: "2fr 1fr auto",
                    },
                    ".fieldRemove": { gridArea: "1 / 3" },
                }}
            >
                {showType ? (
                    <EdgeTypeSelect
                        readOnly={readOnly || types.length < 2}
                        edge={edge}
                        onChange={onTypeChange}
                        options={types}
                        className="edge-type"
                    />
                ) : null}

                {edge.edgeType?.type === EdgeKind.switchNext ? (
                    <EditableEditor
                        variableTypes={variableTypes}
                        fieldLabel={t("node.fields.edge.expression", "Expression")}
                        expressionObj={{
                            expression: edge.edgeType?.condition?.expression !== undefined ? edge.edgeType?.condition?.expression : "true",
                            language: edge.edgeType?.condition?.language || ExpressionLang.SpEL,
                        }}
                        readOnly={readOnly}
                        onValueChange={onValueChange}
                        fieldErrors={fieldErrors}
                        showValidation
                        showSwitch={false}
                        valueClassName={"edge-value"}
                        inputAdornmentEnd={conditionBuilderAdornment}
                    />
                ) : null}

                <TypeSelect
                    title={
                        targetOptions.length
                            ? t("node.fields.edge.target", "Edge target node")
                            : t("node.fields.edge.target.empty", "No free target nodes")
                    }
                    onChange={onTargetChange}
                    value={
                        readOnly
                            ? { value: edge.to, label: nodeNameById[edge.to] ?? edge.to }
                            : targetOptions.find((option) => option.value === edge.to)
                    }
                    options={targetOptions}
                    readOnly={readOnly || targetOptions.length <= 1}
                    className="edge-target"
                />
            </FieldsRow>
            {conditionBuilderOpen && sourceNode && (
                <Dialog open onClose={() => setConditionBuilderOpen(false)} maxWidth="lg" fullWidth>
                    <DataMapperDialogTitle node={sourceNode} onClose={() => setConditionBuilderOpen(false)} title="condition builder" />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <ConditionBuilder
                            onInsert={(spel) => {
                                onValueChange({ expression: spel, language: ExpressionLang.SpEL });
                                setConditionBuilderOpen(false);
                            }}
                            variableTypes={variableTypes}
                            contextData={conditionBuilderContextData}
                            initialExpression={
                                edge.edgeType?.condition?.language === ExpressionLang.SpEL &&
                                edge.edgeType?.condition?.expression !== "true"
                                    ? edge.edgeType?.condition?.expression
                                    : undefined
                            }
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
