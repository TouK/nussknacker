import { styled } from "@mui/material";
import { IconModalTitle } from "./IconModalTitle";
import { blendDarken, blendLighten } from "../../../../containers/theme/helpers";
import { getLuminance } from "@mui/system/colorManipulator";

export const Subtype = styled(IconModalTitle)(({ theme }) => {
    const backgroundColor =
        getLuminance(theme.palette.background.paper) < 0.5
            ? blendDarken(theme.palette.background.paper, 0.2)
            : blendLighten(theme.palette.background.paper, 0.2);
    const color = theme.palette.text.secondary;
    return {
        color,
        backgroundColor,
        padding: theme.spacing(0, 1.6),
        columnGap: theme.spacing(1),

        "a > &": {
            color: color,
            ":hover": {
                color: theme.palette.text.primary,
                backgroundColor:
                    getLuminance(backgroundColor) < 0.5 ? blendDarken(backgroundColor, 0.2) : blendLighten(backgroundColor, 0.2),
            },
        },
    };
});
