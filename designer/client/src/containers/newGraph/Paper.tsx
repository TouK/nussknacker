import { Box } from "@mui/material";
import { BoxProps } from "@mui/material/Box/Box";
import { dia } from "jointjs";
import React, { useEffect, useRef, useState } from "react";
import { PanZoomPlugin } from "../../components/graph/PanZoomPlugin";
import { Canvas } from "./Canvas";
import { useGraph } from "./GraphProvider";
import { createContextHook, useContextForward } from "./utils";

type ContextType = { paper: dia.Paper; panZoom: PanZoomPlugin };
const PaperContext = React.createContext<ContextType>(null);

export type PaperProps = BoxProps;

export const Paper = React.forwardRef<ContextType, PaperProps>(function Paper({ children, ...props }, forwardedRef) {
    const canvasRef = useRef<HTMLElement>(null);
    const model = useGraph();

    const [context, setContext] = useState<ContextType>({ paper: null, panZoom: null });
    useEffect(() => {
        setContext(() => {
            const paper = new dia.Paper({
                width: "auto",
                height: "auto",
                el: canvasRef.current,
                model,
            });
            return {
                paper,
                panZoom: new PanZoomPlugin(paper),
            };
        });
    }, [model]);

    useContextForward(forwardedRef, context);

    useEffect(() => {
        context.panZoom?.fitContent();
    }, [context.panZoom]);

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
            >
                <PaperContext.Provider value={context}>{children}</PaperContext.Provider>
            </Canvas>
        </Box>
    );
});

export const usePaper = createContextHook(PaperContext, Paper);
