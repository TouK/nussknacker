import { styled } from "@mui/material";
import { getLuminance } from "@mui/system/colorManipulator";
import { DefaultComponents as Window } from "@touk/window-manager";
import { blendDarken, blendLighten } from "../../../../containers/theme/helpers";
import { HeaderWithGlobalCursor } from "./HeaderWithGlobalCursor";

export const StyledHeader = styled(HeaderWithGlobalCursor)(({ theme }) => {
    return {
        "--backgroundColor":
            getLuminance(theme.palette.background.paper) > 0.5
                ? blendDarken(theme.palette.background.paper, 0.1)
                : blendLighten(theme.palette.background.paper, 0.1),
        backgroundColor: "var(--backgroundColor)",
    };
});

export const StyledContent = styled(Window.Content)(({ theme }) => {
    return {
        "body :has(>&)": {
            scrollPadding: theme.spacing(3.5),
            scrollPaddingTop: theme.spacing(6),
        },
    };
});
