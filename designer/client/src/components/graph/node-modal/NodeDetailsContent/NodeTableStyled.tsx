import { styled } from "@mui/material";

export const HIDDEN_TEXTAREA_PIXEL_HEIGHT = 100;
export const nodeInput = "node-input";
export const nodeValue = "node-value";
export const nodeInputWithError = "node-input-with-error";
export const rowAceEditor = "row-ace-editor";
export const partlyHidden = "partly-hidden";

export const editorAnchorName = "--editor";

export const NodeTableStyled = styled("div")(({ theme }) => ({
    fontSize: 11,
    margin: "0 24px",

    [`.${nodeValue}`]: {
        flex: 1,
        flexBasis: "60%",
        display: "inline-block",
        width: "100%",
        "anchor-name": editorAnchorName,

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
        '&:has([aria-label="InputAdornmentEnd"])': {
            padding: theme.spacing(1, 2.5, 1, 0.625),
        },
        // TODO: This style matches existing input placeholder styles. We should make it shareable somehow
        "& .ace_placeholder": {
            ...theme.typography.body1,
            margin: 0,
            textOverflow: "inherit",
            overflowWrap: "normal",
            lineHeight: "initial !important",
            WebkitUserModify: "read-only !important",
            whiteSpace: "pre",
            overflow: "hidden",
            color: "rgb(117, 117, 117) !important",
            WebkitTextSecurity: "none",
            direction: "inherit !important",
            pointerEvents: "none !important",
            textOrientation: "inherit !important",
            writingMode: "inherit !important",
            opacity: 1,
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
