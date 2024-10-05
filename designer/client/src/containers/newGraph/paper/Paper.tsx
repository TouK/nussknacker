import { Box } from "@mui/material";
import { BoxProps } from "@mui/material/Box/Box";
import { dia } from "jointjs";
import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { PanZoomPlugin } from "../../../components/graph/PanZoomPlugin";
import { useGraph } from "../GraphProvider";
import { createContextHook, useContextForward } from "../utils";
import { Canvas } from "./Canvas";
import { usePanZoomBehavior } from "./usePanZoomBehavior";

export type PaperContextType = { paper: dia.Paper; panZoom: PanZoomPlugin };
const PaperContext = React.createContext<PaperContextType>(null);

export type PaperProps = BoxProps & {
    interactive?: boolean;
};

export const Paper = React.forwardRef<PaperContextType, PaperProps>(function Paper(
    { children, interactive = false, ...props },
    forwardedRef,
) {
    const [context, setContext] = useState<PaperContextType>({ paper: null, panZoom: null });
    const registerBehavior = useCallback(
        <K extends keyof Omit<PaperContextType, "paper">, B = K extends keyof PaperContextType ? PaperContextType[K] : never>(key: K) =>
            (behavior: B) =>
                behavior && setContext((context) => (behavior === context[key] ? context : { ...context, [key]: behavior })),
        [],
    );

    useContextForward(forwardedRef, context);

    const canvasRef = useRef<HTMLElement>(null);
    const model = useGraph();

    useLayoutEffect(() => {
        const paper = new dia.Paper({
            width: "auto",
            height: "auto",
            el: canvasRef.current,
            interactive: false,
            model,
        });

        setContext((context) => ({ ...context, paper }));

        return () => {
            paper.undelegateDocumentEvents();
            paper.undelegateEvents();
        };
    }, [model, setContext]);

    usePanZoomBehavior(context, registerBehavior("panZoom"), { interactive });

    useEffect(() => {
        const { paper } = context;
        if (paper) {
            paper.setInteractivity(interactive);
        }
    }, [context, interactive]);

    return (
        <Box position="relative" {...props}>
            <Canvas
                sx={{
                    "&&&": {
                        position: "absolute",
                        inset: 0,
                        background: "transparent",
                    },
                }}
                ref={canvasRef}
            ></Canvas>
            <PaperContext.Provider value={context}>{children}</PaperContext.Provider>
        </Box>
    );
});

export const usePaper = createContextHook(PaperContext, Paper);
