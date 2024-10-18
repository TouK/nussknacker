import { styled } from "@mui/material";

export const HIDDEN_TEXTAREA_PIXEL_HEIGHT = 100;
export const nodeInput = "node-input";
export const nodeValue = "node-value";
export const nodeInputWithError = "node-input-with-error";
export const rowAceEditor = "row-ace-editor";
export const movableRow = "movable-row";
export const partlyHidden = "partly-hidden";

export const NodeTableStyled = styled("div")(({ theme }) => ({
    fontSize: 11,
    margin: "0 24px",

    [`.${movableRow}`]: {
        marginTop: 0,
        flexWrap: "nowrap",
        columnGap: 5,
        rowGap: 5,
    },

    [`.${nodeValue}`]: {
        flex: 1,
        flexBasis: "60%",
        display: "inline-block",
        width: "100%",

        textarea: {
            overflow: "hidden",
            height: "auto",
        },

        [`input[type="checkbox"]`]: {
            textRendering: "optimizeSpeed",
            width: 20,
            height: 20,
            margin: 0,
            marginRight: 1,
            display: "block",
            position: "relative",
            cursor: "pointer",
            MozAppearance: "none",
            border: 0,
            marginTop: 7,
            marginBottom: 7,
            "&::after": {
                left: "0",
                top: "0",
                content: `""`,
                verticalAlign: "middle",
                textAlign: "center",
                lineHeight: 1,
                position: "absolute",
                cursor: "pointer",
                height: 20,
                width: 20,
                fontSize: 20 - 6,
                background: theme.palette.background.paper,
            },
            "&:checked::after": {
                content: `"\\2713"`,
                fontSize: "1rem",
            },
            "&:disabled": {
                opacity: 0.3,
                cursor: "auto",
            },
            "&:focus": {
                border: "none",
                "&:after": {
                    outline: `1px solid ${theme.palette.primary.main}`,
                },
            },
        },

        [`&.${partlyHidden}`]: {
            textarea: {
                height: `${HIDDEN_TEXTAREA_PIXEL_HEIGHT}px !important`,
            },
        },
        [`&.${nodeInputWithError}`]: {
            width: "100%",
            maxHeight: 35,
        },
        "&.switchable": {
            width: "70%",
        },
    },

    [`.${nodeInput}`]: {
        height: 35,
        width: "100%",
        padding: "0 10px",
        color: theme.palette.text.primary,
        fontWeight: 400,
        fontSize: 14,
    },

    [`.${rowAceEditor}`]: {
        padding: theme.spacing(1, 0.625),
        minHeight: 35,
        ".ace-nussknacker": {
            outline: "none",
        },
    },

    [`textarea.${nodeInput}`]: {
        resize: "vertical",
        lineHeight: 1.5,
        paddingTop: 7,
        paddingBottom: 7,
    },

    [`input[type="checkbox"].${nodeInput}`]: {
        height: 20,
    },

    [`.${nodeInputWithError}`]: {
        outline: `1px solid ${theme.palette.error.light} !important`,
        outlineOffset: "initial !important",
        borderRadius: 2,
    },

    ".marked": {
        border: `2px solid ${theme.palette.success.main} !important`,
    },
}));
