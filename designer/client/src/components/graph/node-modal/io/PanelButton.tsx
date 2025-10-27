import { alpha, styled } from "@mui/material";
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
        background: alpha(theme.palette.action.hover, 0.025),
        color: theme.palette.text.secondary,
        "&:hover, &:focus, &:active": {
            color: theme.palette.text.primary,
            background: alpha(theme.palette.action.hover, 0.15),
            outline: 0,
        },
        "&, svg": {
            transition: theme.transitions.create(["transform", "color", "fill", "background"]),
        },
        svg: {
            fontSize: "1.2rem",
            marginBottom: -3,
            transform: `rotate(${collapsed ? 180 : 0}deg)`,
        },
        backdropFilter: "blur(15px)",
        borderRadius: theme.shape.borderRadius * 2,
        paddingInline: 15,
    };
    switch (side) {
        case "left":
            return {
                ...styles,
                left: 0,
                transformOrigin: "left",
                transform: `rotate(-90deg) translateX(-50%)`,
                paddingBlockStart: 10,
            };
        case "right":
            return {
                ...styles,
                right: 0,
                transformOrigin: "right",
                transform: `rotate(90deg) translateX(50%)`,
                paddingBlockStart: 10,
            };
        case "center":
            return {
                ...styles,
                left: "50%",
                bottom: "100%",
                transform: "translateY(50%) translateX(-50%)",
                backgroundColor: "transparent",
            };
        default:
            return styles;
    }
});
