/* eslint-disable i18next/no-literal-string */
import React, { createContext, PropsWithChildren, useContext, useMemo } from "react";
import { Graph } from "./Graph";

type GraphContextType = () => Graph | null;

const GraphContext = createContext<GraphContextType | null>(null);

export const GraphProvider = ({
    graph: _graphGetter,
    children,
}: PropsWithChildren<{
    graph: GraphContextType;
}>) => {
    const graph = _graphGetter();

    // force new value
    // eslint-disable-next-line react-hooks/exhaustive-deps
    const graphGetter = useMemo(() => () => _graphGetter(), [_graphGetter, graph]);

    return <GraphContext.Provider value={graphGetter}>{children}</GraphContext.Provider>;
};

export const useGraph = (): GraphContextType => {
    const graphGetter = useContext(GraphContext);
    if (!graphGetter) {
        throw "no graph getter provided!";
    }
    return graphGetter;
};
