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

export type PaperProps = BoxProps & {
    interactive?: boolean;
};

export const Paper = React.forwardRef<ContextType, PaperProps>(function Paper({ children, interactive = false, ...props }, forwardedRef) {
    const canvasRef = useRef<HTMLElement>(null);
    const model = useGraph();

    const [context, setContext] = useState<ContextType>({ paper: null, panZoom: null });
    useEffect(() => {
        const paper = new dia.Paper({
            width: "auto",
            height: "auto",
            el: canvasRef.current,
            interactive: false,
            model,
        });
        const panZoom = new PanZoomPlugin(paper);

        setContext({ paper, panZoom });

        return () => {
            paper.undelegateDocumentEvents();
            paper.undelegateEvents();
            panZoom.remove();
        };
    }, [model]);

    useContextForward(forwardedRef, context);

    useEffect(() => {
        const { paper, panZoom } = context;
        if (paper) {
            panZoom.fitContent();
            panZoom.toggle(interactive);
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
            >
                <PaperContext.Provider value={context}>{children}</PaperContext.Provider>
            </Canvas>
        </Box>
    );
});

export const usePaper = createContextHook(PaperContext, Paper);
