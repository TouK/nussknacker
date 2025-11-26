import { useCallback, useLayoutEffect, useMemo, useRef } from "react";

import { getAdditionalFields } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import type { NodeState } from "./node/useNodeState";
import { getDynamicParameterDefinitions } from "./NodeDetailsContent/selectors";
import { generateUUIDs } from "./nodeUtils";
import { adjustParameters } from "./ParametersUtils";
import { wrapSetState } from "./wrapSetState";

export function useNodeAdjust(
    node: NodeType,
    onChange?: NodeState["onChange"],
): [adjustedNode: typeof node, adjustedOnChange: typeof onChange] {
    const getParameterDefinitions = useAppSelector(getDynamicParameterDefinitions);
    const { properties: storedProperties } = useAppSelector(getAdditionalFields);

    const adjustNode = useCallback(
        (node: NodeType) => {
            const parameterDefinitions = getParameterDefinitions(node);
            const adjustedNode = adjustParameters(node, parameterDefinitions, storedProperties);
            return generateUUIDs(adjustedNode, ["fields", "parameters"]);
        },
        [getParameterDefinitions, storedProperties],
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
