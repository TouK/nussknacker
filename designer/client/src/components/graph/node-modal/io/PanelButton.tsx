import { styled } from "@mui/material";
import type { CSSObject } from "@mui/styled-engine";

export const PanelButton = styled("button", {
    shouldForwardProp: (prop) => prop !== "side" && prop !== "collapsed",
})<{
    side?: "center" | "left" | "right";
    collapsed?: boolean;
}>(({ side, collapsed, theme }) => {
    const styles: CSSObject = {
        position: "absolute",
        bottom: "50%",
        zIndex: 20,
        padding: 0,
        margin: 0,
        border: 0,
        outline: 0,
        lineHeight: 0,
        background: "transparent",
        "&:focus": {
            color: theme.palette.action.active,
        },
        svg: {
            fontSize: "1.2rem",
        },
    };
    switch (side) {
        case "left":
            return {
                ...styles,
                left: 0,
                transform: `translateY(-50%) translateX(-35%) rotate(${collapsed ? 90 : -90}deg)`,
                paddingInline: 20,
            };
        case "right":
            return {
                ...styles,
                right: 0,
                transform: `translateY(-50%) translateX(35%) rotate(${collapsed ? -90 : 90}deg)`,
                paddingInline: 20,
            };
        case "center":
            return {
                ...styles,
                left: "50%",
                bottom: "100%",
                transform: "translateY(50%) translateX(-50%)",
                paddingInline: 20,
                "&:focus": {
                    outline: 0,
                    color: "inherit",
                },
            };
        default:
            return styles;
    }
});
