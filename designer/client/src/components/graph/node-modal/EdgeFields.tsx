import { produce } from "immer";
import { isEqual, uniq } from "lodash";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { Edge } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeId } from "../../../types/node";
import type { VariableTypes } from "../../../types/validation";
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

    const showType = useMemo(() => types.length > 1 || uniq(edges.map((e) => e.edgeType?.type)).length > 1, [edges, types.length]);
    return (
        <FieldsRow
            uuid={edge._id}
            index={index}
            sx={{
                "&&&&": {
                    display: "grid",
                    gridTemplateColumns: "1fr 2fr auto",
                },
                ".fieldRemove": { gridArea: "1 / 3" },
                "& :has(>.edge-value)": { gridColumn: "span 2" },
                "& :has(>.edge-value) + .edge-target": { gridColumn: "span 2" },
                "& .edge-type + :has(>.edge-value)": { gridColumn: "span 1" },
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
    );
}
