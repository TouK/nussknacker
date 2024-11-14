import { Box } from "@mui/material";
import { BoxProps } from "@mui/material/Box/Box";
import { dia, shapes } from "jointjs";
import React, { useEffect, useImperativeHandle, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useGraph } from "../GraphProvider";
import { createContextHook } from "../utils/context";
import { Canvas } from "./Canvas";

class ExtendedPaper extends dia.Paper {
    protected _removeElement() {
        // avoid removing Canvas element
        this.removeLayers();
    }
}

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

    useImperativeHandle(forwardedRef, () => context, [context]);

    const canvasRef = useRef<HTMLElement>(null);
    const model = useGraph();

    useLayoutEffect(() => {
        const paper = new ExtendedPaper({
            width: "auto",
            height: "auto",
            el: canvasRef.current,
            interactive: false,
            model,
            cellViewNamespace: shapes,
        });

        setContext((context) => ({ ...context, paper }));

        return () => {
            paper.remove();
        };
    }, [model, setContext]);

    const { paper } = context;

    useEffect(() => {
        if (paper) {
            paper.setInteractivity(interactive);
        }
    }, [paper, interactive]);

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
            <PaperContext.Provider value={context}>{children}</PaperContext.Provider>
        </Box>
    );
});

export const usePaper = createContextHook(PaperContext, Paper);

export const PaperSvgPortal = ({ children }: React.PropsWithChildren) => {
    const { paper } = usePaper();
    return createPortal(<>{children}</>, paper.svg);
};
