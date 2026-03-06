import { useCallback, useMemo } from "react";

import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";

export function useScenarioNodeOrder() {
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const nodes = useMemo(() => scenarioGraph.nodes ?? [], [scenarioGraph.nodes]);

    const nodeOrderMap = useMemo(
        () =>
            nodes.reduce((acc, node, index) => {
                acc[node.id] = index;
                return acc;
            }, {} as Record<string, number>),
        [nodes],
    );

    const sortByScenarioOrder = useCallback(
        (ids: string[]) => [...ids].sort((a, b) => (nodeOrderMap[a] ?? Infinity) - (nodeOrderMap[b] ?? Infinity)),
        [nodeOrderMap],
    );

    return { nodes, nodeOrderMap, sortByScenarioOrder };
}
