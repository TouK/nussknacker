import { rgbToHex, Theme } from "@mui/material";
import { blend } from "@mui/system";

export const blendDarken = (color: string, opacity: number) => rgbToHex(blend(color, "#000000", opacity));
export const blendLighten = (color: string, opacity: number) => rgbToHex(blend(color, "#ffffff", opacity));
export const getBorderColor = (theme: Theme) => blendLighten(theme.palette.background.paper, 0.25);
