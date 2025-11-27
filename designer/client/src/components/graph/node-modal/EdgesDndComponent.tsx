import { defaultsDeep } from "lodash";
import React, { useCallback, useMemo } from "react";

import type { Edge } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import { DndItems } from "../../common/dndItems/DndItems";
import NodeUtils from "../NodeUtils";
import { EdgeFields } from "./EdgeFields";
import type { EdgeTypeOption } from "./EdgeTypeSelect";
import { ExpressionLang } from "./editors/expression/types";
import { getValidationErrorsForField } from "./editors/Validators";
import { NodeRowFieldsProvider } from "./node-row-fields-provider/NodeRowFieldsProvider";
import type { WithTempId } from "./tempId";
import { useStateWithTempId, withTempId } from "./tempId";

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

export function EdgesDndComponent(props: Props): React.JSX.Element {
    const { nodeId, label, readOnly, value, onChange, ordered, variableTypes, errors } = props;
    const [edges, setEdges] = useStateWithTempId(value, onChange);

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
                setEdges((edges) => edges.map((e) => (e === current ? withTempId(withDefaults(next)) : e)));
            }
        },
        [setEdges],
    );

    const removeEdge = useCallback((n, uuid: string) => setEdges((edges) => edges.filter((e) => e._id !== uuid)), [setEdges]);

    const addEdge = useCallback(() => {
        const [{ value: type }] = availableTypes;
        setEdges((edges) => edges.concat(withTempId(withDefaults({ from: nodeId, edgeType: { type } }))));
    }, [availableTypes, nodeId, setEdges]);

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
