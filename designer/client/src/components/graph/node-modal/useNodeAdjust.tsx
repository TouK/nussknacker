import { produce } from "immer";
import { useCallback, useLayoutEffect, useMemo, useRef } from "react";

import { getAdditionalFields } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import { appendUuidToParameters } from "./appendUuid";
import type { NodeState } from "./node/useNodeState";
import { adjustParameters } from "./ParametersUtils";
import { useParameterDefinitions } from "./useNodeTypeDetailsContentLogic";
import { wrapSetState } from "./wrapSetState";

export function useNodeAdjust(
    node: NodeType,
    onChange?: NodeState["onChange"],
): [adjustedNode: typeof node, adjustedOnChange: typeof onChange] {
    const parameterDefinitions = useParameterDefinitions({ node });
    const { properties: storedProperties } = useAppSelector(getAdditionalFields);

    const adjustNode = useCallback(
        (node: NodeType) => {
            const adjustedNode = adjustParameters(node, parameterDefinitions, storedProperties);
            return produce(adjustedNode, appendUuidToParameters);
        },
        [parameterDefinitions, storedProperties],
    );

    const adjustFn = useRef(adjustNode);
    useLayoutEffect(() => {
        adjustFn.current = adjustNode;
    }, [adjustNode]);

    const adjustedNode = useMemo<typeof node>(() => adjustNode(node), [adjustNode, node]);

    const adjustedOnChange = useCallback<typeof onChange>(
        (setNodeAction, setEdgesAction) => onChange?.(wrapSetState(setNodeAction, adjustFn?.current), setEdgesAction),
        [onChange],
    );

    return [adjustedNode, adjustedOnChange];
}
