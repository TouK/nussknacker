import { useCallback, useMemo } from "react";

import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";

export function useScenarioNodeOrder() {
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const nodes = useMemo(() => scenarioGraph.nodes ?? [], [scenarioGraph.nodes]);

    const sortByScenarioOrder = useCallback(
        (ids: string[]) => [...ids].sort((a, b) => (nodes[a] ?? Infinity) - (nodes[b] ?? Infinity)),
        [nodes],
    );

    return { nodes, sortByScenarioOrder };
}
