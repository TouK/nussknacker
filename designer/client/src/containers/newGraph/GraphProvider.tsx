import { dia } from "jointjs";
import React, { useState } from "react";
import { createContextHook, useContextForward } from "./utils";

type ContextType = dia.Graph;
const GraphContext = React.createContext<ContextType>(null);

export const GraphProvider = React.forwardRef<ContextType, React.PropsWithChildren>(function GraphProvider({ children }, forwardedRef) {
    const [graph] = useState<dia.Graph>(() => new dia.Graph());

    useContextForward(forwardedRef, graph);

    return <GraphContext.Provider value={graph}>{children}</GraphContext.Provider>;
});

export const useGraph = createContextHook(GraphContext, GraphProvider);
