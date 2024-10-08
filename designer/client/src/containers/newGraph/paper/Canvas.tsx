import { Box } from "@mui/material";
import { BoxProps } from "@mui/material/Box/Box";
import React from "react";
import { createContextHook } from "../utils/context";

type ContextType = React.Ref<HTMLElement>;
const CanvasContext = React.createContext<ContextType>(null);

export type CanvasProps = BoxProps;

export const Canvas = React.forwardRef<HTMLElement, CanvasProps>(function CanvasProvider({ children, ...props }, forwardedRef) {
    return (
        <>
            <CanvasContext.Provider value={forwardedRef}>{children}</CanvasContext.Provider>
            <Box {...props} ref={forwardedRef} />
        </>
    );
});

export const useCanvas = createContextHook(CanvasContext, Canvas);
