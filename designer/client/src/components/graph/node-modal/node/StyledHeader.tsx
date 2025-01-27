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
            "::-webkit-scrollbar-track": {
                width: "8px",
                height: "8px",
                background: blendLighten(theme.palette.background.paper, 0.1),
            },
            "::-webkit-scrollbar-thumb": {
                background: blendLighten(theme.palette.background.paper, 0.5),
                backgroundClip: "content-box",
                border: "2px solid transparent",
                borderRadius: "100px",
                height: "15px",
            },
            "::-webkit-scrollbar-thumb:hover": {
                background: blendLighten(theme.palette.background.paper, 0.5),
            },
            "::-webkit-scrollbar-corner": {
                background: theme.palette.background.paper,
            },
            "::-webkit-scrollbar": {
                paddingTop: theme.spacing(2),
                width: "8px",
                height: "8px",
            },
        },
    };
});
