import { rgbToHex, Theme } from "@mui/material";
import { blend } from "@mui/system";
import { getLuminance } from "@mui/system/colorManipulator";

export const blendDarken = (color: string, opacity: number) => rgbToHex(blend(color, "#000000", opacity));
export const blendLighten = (color: string, opacity: number) => rgbToHex(blend(color, "#ffffff", opacity));
export const getBorderColor = (theme: Theme) =>
    getLuminance(theme.palette.background.paper) > 0.5
        ? blendDarken(theme.palette.background.paper, 0.25)
        : blendLighten(theme.palette.background.paper, 0.25);

export function getNodeBorderColor(theme: Theme) {
    return getLuminance(theme.palette.background.paper) > 0.5
        ? blendDarken(theme.palette.background.paper, 0.4)
        : blendLighten(theme.palette.background.paper, 0.6);
}
