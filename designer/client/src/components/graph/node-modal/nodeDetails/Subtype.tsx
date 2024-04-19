import { styled } from "@mui/material";
import { IconModalTitle } from "./IconModalTitle";
import { blendDarken, blendLighten } from "../../../../containers/theme/helpers";
import { getLuminance } from "@mui/system/colorManipulator";

const getColorBlend = (color: string, value: number) =>
    getLuminance(color) > 0.5 ? blendDarken(color, value) : blendLighten(color, value);

export const Subtype = styled(IconModalTitle)(({ theme }) => {
    const backgroundColor = getColorBlend(theme.palette.background.paper, 0.1);
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
                backgroundColor: getColorBlend(backgroundColor, 0.1),
            },
        },
    };
});
