import { defaultsDeep, isEqual } from "lodash";
import React, { useCallback, useEffect, useMemo, useRef } from "react";

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
import { useCallbackRef } from "./node/useCallbackRef";
import { useStream } from "./node/useStream";
import { withoutTempId, withTempId } from "./tempId";

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
    isValidating: boolean;
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
    return { from: "", to: "", edgeType: getDefaultEdgeType(kind) };
}

function withDefaults<T extends Edge>(edge: Partial<T>): T {
    return defaultsDeep(edge, getDefaultEdge(EdgeKind[edge.edgeType?.type]));
}

export function EdgesDndComponent(props: Props): React.JSX.Element {
    const { nodeId, label, readOnly, value, onChange, ordered, variableTypes, errors, isValidating } = props;

    const lastEmittedValueRef = useRef<Edge[]>();
    const [onChangeRef] = useCallbackRef(
        (edges) => {
            lastEmittedValueRef.current = edges;
            onChange(edges);
        },
        [onChange],
    );

    const [edge$, setEdges, edges] = useStream<Edge[]>(
        value.map((edge, index) => withTempId(withDefaults(edge), index)),
        false, // Don't auto-update to avoid infinite loop
    );

    // Sync external value changes (but not our own changes)
    useEffect(() => {
        // Only update if value changed from outside (not from our own onChange)
        if (!isEqual(lastEmittedValueRef.current, value)) {
            const externalEdges = value.map((edge, index) => withTempId(withDefaults(edge), index));
            setEdges(externalEdges);
        }
    }, [value, setEdges]);

    useEffect(() => {
        const subscription = edge$.observe((edges) => {
            onChangeRef.current?.(edges.map(withoutTempId));
        });
        return subscription.unsubscribe;
    }, [edge$, onChangeRef]);

    const edgeTypes = useMemo(
        () => props.edgeTypes.map((t) => ({ ...t, label: t.label || NodeUtils.edgeTypeLabel(t.value) })),
        [props.edgeTypes],
    );

    const availableTypes = useMemo(
        () => edgeTypes.filter((t) => !t.onlyOne || !edges.some((e) => e.edgeType?.type === t.value)),
        [edgeTypes, edges],
    );

    const replaceEdge = useCallback(
        (edgeId: string, next: Edge) => {
            setEdges((edges) =>
                edges.map((e) => {
                    if (e._id === edgeId) {
                        const updated = { ...next, _id: e._id };
                        return isEqual(e, updated) ? e : updated;
                    }
                    if (e.to && e.to === next.to) {
                        const current = edges.find((edge) => edge._id === edgeId);
                        return { ...e, to: current?.to || "" };
                    }
                    return e;
                }),
            );
        },
        [setEdges],
    );

    const removeEdge = useCallback(
        (n, uuid: string) => {
            setEdges((edges) => edges.filter((e) => e._id !== uuid));
        },
        [setEdges],
    );

    const addEdge = useCallback(() => {
        const [{ value: type }] = availableTypes;
        setEdges((edges) => {
            const item = withTempId(withDefaults({ from: nodeId, edgeType: { type } }), edges.length);
            return [...edges, item];
        });
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
                        edgeId={edge._id || edge.to}
                        index={index}
                        readOnly={readOnly}
                        value={edge}
                        onChange={replaceEdge}
                        edges={array}
                        types={types}
                        variableTypes={variableTypes}
                        fieldErrors={getValidationErrorsForField(errors, edge._id || edge.to)}
                        isValidating={isValidating}
                    />
                ),
            };
        });
    }, [edgeTypes, edges, errors, isValidating, readOnly, replaceEdge, variableTypes]);

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
