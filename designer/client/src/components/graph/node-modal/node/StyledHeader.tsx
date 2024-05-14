import { styled } from "@mui/material";
import { DefaultComponents as Window } from "@touk/window-manager";

import { blendDarken, blendLighten } from "../../../../containers/theme/helpers";
import { getLuminance } from "@mui/system/colorManipulator";

export const StyledHeader = styled(Window.Header)(({ isMaximized, isStatic, theme }) => {
    const draggable = !isMaximized && !isStatic;
    return {
        backgroundColor:
            getLuminance(theme.palette.background.paper) > 0.5
                ? blendDarken(theme.palette.background.paper, 0.1)
                : blendLighten(theme.palette.background.paper, 0.1),
        cursor: draggable ? "grab" : "inherit",
        ":active": {
            cursor: draggable ? "grabbing" : "inherit",
        },
    };
});
