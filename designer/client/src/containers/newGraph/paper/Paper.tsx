import { Box } from "@mui/material";
import { BoxProps } from "@mui/material/Box/Box";
import { dia } from "jointjs";
import React, { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useGraph } from "../GraphProvider";
import { createContextHook, useContextForward } from "../utils/context";
import { Canvas } from "./Canvas";
import { DropBehavior } from "./DropBehavior";
import { PanWithCellBehavior } from "./PanWithCellBehavior";
import { PanZoomBehavior } from "./PanZoomBehavior";

export type PaperContextType = { paper: dia.Paper };
const PaperContext = React.createContext<PaperContextType>(null);

export type PaperProps = BoxProps & {
    interactive?: boolean;
};

export const Paper = React.forwardRef<PaperContextType, PaperProps>(function Paper(
    { children, interactive = false, ...props },
    forwardedRef,
) {
    const [context, setContext] = useState<PaperContextType>({ paper: null });

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

    // usePanZoomBehavior([context, registerBehavior("panZoom")], behaviorProps);
    // usePanWithCellBehavior([context, registerBehavior("edgePan")], behaviorProps);
    // const isDraggingOver = useDropBehavior(behaviorProps);

    useEffect(() => {
        const { paper } = context;
        if (paper) {
            paper.setInteractivity(interactive);
        }
    }, [context, interactive]);

    return (
        <Box position="relative" {...props}>
            <Canvas
                style={
                    {
                        // background: false ? "red" : "transparent",
                    }
                }
                sx={{
                    "&&&": {
                        position: "absolute",
                        inset: 0,
                        background: "transparent",
                    },
                }}
                ref={canvasRef}
            />
            <PaperContext.Provider value={context}>
                <PanZoomBehavior interactive={interactive}>
                    <PanWithCellBehavior>
                        <DropBehavior interactive={interactive} />
                    </PanWithCellBehavior>
                    {children}
                </PanZoomBehavior>
            </PaperContext.Provider>
        </Box>
    );
});

export const usePaper = createContextHook(PaperContext, Paper);
