import { defaultsDeep } from "lodash";
import React, { useCallback, useMemo, useState, useRef, useEffect } from "react";
import { useSelector } from "react-redux";

import { useFirstRender } from "../../../containers/hooks/useFirstRender";
import { getScenarioGraph } from "../../../reducers/selectors/graph";
import type { Edge, NodeValidationError, VariableTypes } from "../../../types";
import { EdgeKind } from "../../../types";
import { DndItems } from "../../common/dndItems/DndItems";
import NodeUtils from "../NodeUtils";
import { EdgeFields } from "./EdgeFields";
import type { EdgeTypeOption } from "./EdgeTypeSelect";
import { ExpressionLang } from "./editors/expression/types";
import { getValidationErrorsForField } from "./editors/Validators";
import { NodeRowFieldsProvider } from "./node-row-fields-provider";

interface EdgeType extends Partial<EdgeTypeOption> {
    value: EdgeKind;
    label?: string;
    onlyOne?: boolean;
}

interface Props {
    nodeId: string;
    label: string;
    value?: Edge[];
    onChange?: (edges: Edge[]) => void;
    readOnly?: boolean;
    edgeTypes: EdgeType[];
    ordered?: boolean;
    variableTypes?: VariableTypes;
    errors: NodeValidationError[];
}

export type WithTempId<T> = T & { _id?: string };

//mutate to avoid unnecessary renders
function withFakeId(edge: WithTempId<Edge>): WithTempId<Edge> {
    if (!edge._id) {
        edge._id = `id${Math.random()}`;
    }
    return edge;
}

function getDefaultEdgeType(kind: EdgeKind): Edge["edgeType"] {
    switch (kind) {
        case EdgeKind.switchNext:
            return {
                type: kind,
                condition: {
                    expression: "true",
                    language: ExpressionLang.SpEL,
                },
            };
        default:
            return { type: kind };
    }
}

function getDefaultEdge(kind: EdgeKind): Edge {
    return { _id: `id${Math.random()}`, from: "", to: "", edgeType: getDefaultEdgeType(kind) };
}

function withDefaults<T extends Edge>(edge: Partial<T>): T {
    return defaultsDeep(edge, getDefaultEdge(edge.edgeType?.type));
}

export function EdgesDndComponent(props: Props): JSX.Element {
    const isFirstRender = useFirstRender();

    const { nodeId, label, readOnly, value, onChange, ordered, variableTypes, errors } = props;
    const process = useSelector(getScenarioGraph);
    const [edges, setEdges] = useState<WithTempId<Edge>[]>(() => {
        const edges1 = value || process.edges.filter(({ from }) => from === nodeId);
        return edges1.map((edge) => withFakeId({ ...edge }));
    });

    const edgeTypes = useMemo(
        () => props.edgeTypes.map((t) => ({ ...t, label: t.label || NodeUtils.edgeTypeLabel(t.value) })),
        [props.edgeTypes],
    );

    const availableTypes = useMemo(
        () => edgeTypes.filter((t) => !t.onlyOne || !edges.some((e) => e.edgeType?.type === t.value)),
        [edgeTypes, edges],
    );

    const replaceEdge = useCallback(
        (current: WithTempId<Edge>) => (next: WithTempId<Edge>) => {
            if (current !== next) {
                setEdges((edges) => edges.map((e) => (e === current ? withFakeId(withDefaults(next)) : e)));
            }
        },
        [],
    );

    const removeEdge = useCallback((n, uuid: string) => setEdges((edges) => edges.filter((e) => e._id !== uuid)), []);

    const addEdge = useCallback(() => {
        const [{ value: type }] = availableTypes;
        setEdges((edges) => edges.concat(withFakeId(withDefaults({ from: nodeId, edgeType: { type } }))));
    }, [availableTypes, nodeId]);

    useEffect(() => {
        if (isFirstRender()) {
            return;
        }

        onChange?.(edges?.map((e) => ({ ...e, to: e.to || e._id })));
    }, [edges, isFirstRender, onChange]);

    const edgeItems = useMemo(() => {
        return edges.map((edge, index, array) => {
            const types = edgeTypes.filter(
                (t) => t.value === edge.edgeType?.type || (!t.disabled && (!t.onlyOne || !array.some((e) => e.edgeType?.type === t.value))),
            );

            return {
                item: edge,
                el: (
                    <EdgeFields
                        key={edge._id || edge.to}
                        index={index}
                        readOnly={readOnly}
                        value={edge}
                        onChange={replaceEdge(edge)}
                        edges={array}
                        types={types}
                        variableTypes={variableTypes}
                        fieldErrors={getValidationErrorsForField(errors, edge._id || edge.to)}
                    />
                ),
            };
        });
    }, [edgeTypes, edges, errors, readOnly, replaceEdge, variableTypes]);

    const namespace = `edges`;

    return (
        <NodeRowFieldsProvider
            label={label}
            path={namespace}
            readOnly={readOnly}
            onFieldRemove={removeEdge}
            onFieldAdd={availableTypes.length ? addEdge : null}
        >
            <DndItems disabled={readOnly || !ordered} items={edgeItems} onChange={setEdges} />
        </NodeRowFieldsProvider>
    );
}
