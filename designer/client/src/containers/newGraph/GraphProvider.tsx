import { shapes } from "jointjs";
import { debounce } from "lodash";
import React, { useEffect, useImperativeHandle, useState } from "react";
import { Graph } from "./Graph";
import { createContextHook } from "./utils/context";

type GraphContextType = Graph;
const GraphContext = React.createContext<GraphContextType>(null);

export type GraphProviderProps = React.PropsWithChildren<{
    onLayoutChange?: (layout: { id: string; x: number; y: number }[]) => void;
}>;

export const GraphProvider = React.forwardRef<GraphContextType, GraphProviderProps>(function GraphProvider(
    { children, onLayoutChange },
    forwardedRef,
) {
    const [graph] = useState<GraphContextType>(() => {
        return new Graph({}, { cellNamespace: shapes });
    });

    useEffect(() => {
        if (onLayoutChange) {
            const callback = debounce((): void => {
                onLayoutChange(
                    graph.getElements().map((el) => {
                        const { x, y } = el.position();
                        const id = el.id.toString();
                        return { id, x, y };
                    }),
                );
            }, 500);
            graph.on("change:position", callback);
            return () => {
                graph.off("change:position", callback);
            };
        }
    }, [graph, onLayoutChange]);

    useImperativeHandle(forwardedRef, () => graph, [graph]);

    return <GraphContext.Provider value={graph}>{children}</GraphContext.Provider>;
});

export const useGraph = createContextHook(GraphContext, GraphProvider);
