import { Box, GlobalStyles } from "@mui/material";
import { DefaultComponents as Window } from "@touk/window-manager";
import React from "react";

// TODO: consider moving this cursor logic to nk-windows
export const HeaderWithGlobalCursor: typeof Window.Header = (props) => {
    const draggable = !props.isMaximized && !props.isStatic;

    // TODO: consider using library independent className, maybe append some in nk-windows
    const draggingClass = "react-draggable-transparent-selection";
    const rootSelector = `body.${draggingClass}`;

    return (
        <>
            {draggable && (
                <GlobalStyles
                    styles={{
                        [`${rootSelector} *`]: {
                            cursor: "inherit !important",
                        },
                    }}
                />
            )}
            <Box
                component={Window.Header}
                {...props}
                sx={
                    draggable && {
                        cursor: "grab",
                        [`${rootSelector}:has(&)`]: {
                            cursor: "grabbing",
                        },
                    }
                }
            />
        </>
    );
};
