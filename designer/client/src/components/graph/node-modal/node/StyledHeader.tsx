import { styled } from "@mui/material";
import { DefaultComponents as Window } from "@touk/window-manager";
import { getColorBlend } from "../nodeDetails/Subtype";

export const StyledHeader = styled(Window.Header)(({ isMaximized, isStatic, theme }) => {
    const draggable = !isMaximized && !isStatic;
    const backgroundColor = getColorBlend(theme.palette.background.paper, 0.1);

    return {
        backgroundColor,
        cursor: draggable ? "grab" : "inherit",
        ":active": {
            cursor: draggable ? "grabbing" : "inherit",
        },
    };
});
