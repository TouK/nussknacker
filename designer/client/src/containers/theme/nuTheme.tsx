import { tintPrimary } from "./helpers";
import { createTheme } from "@mui/material";

declare module "@mui/material/FormHelperText" {
    interface FormHelperTextPropsVariantOverrides {
        largeMessage: true;
    }
    interface FormHelperTextOwnProps {
        "data-testid"?: string;
    }
}

declare module "@mui/material/styles" {
    // eslint-disable-next-line @typescript-eslint/no-empty-interface
    interface Theme {
        custom: typeof custom;
    }
    // eslint-disable-next-line @typescript-eslint/no-empty-interface
    interface ThemeOptions {
        custom?: typeof custom;
    }
}

const [d, d1, d2, d3, d4, base, l4, l3, l2, l1, l] = [
    // eslint-disable-next-line i18next/no-literal-string
    "#000000",
    "#1A1A1A",
    "#333333",
    "#4D4D4D",
    "#666666",
    "#808080",
    "#999999",
    "#B3B3B3",
    "#CCCCCC",
    "#E6E6E6",
    "#FFFFFF",
];

const colors = {
    borderColor: d,
    canvasBackground: l3,
    primaryBackground: d3,
    secondaryBackground: d2,
    primaryColor: l,
    secondaryColor: l2,
    mutedColor: base,
    focusColor: d1,
    baseColor: l4,
    evenBackground: d3,
    cobalt: "#0058A9",
    mineShaft: "#3e3e3e",
    tundora: d3,
    scorpion: "#5D5D5D",
    silverChalice: "#afafaf",
    cerulean: "#0E9AE0",
    doveGray: d4,
    charcoal: "#444444",
    accent: "#668547",
    eclipse: "#393939",
    nightRider: "#2d2d2d",
    revolver: "#333344",
    curiousBlue: "#337AB7",
    pictonBlue: "#359AF1",
    abbey: "#4A4A4A",
    dustyGray: l4,
    gallery: "#eeeeee",
    boulder: "#777777",
    dimGray: "#686868",
    eucalyptus: "#33A369",
    seaGarden: "#2D8E54",
    lawnGreen: "#7EDB0D",
    red: "#FF0000",
    yellow: "#ffff00",
    deepskyblue: "#00bfff",
    lime: "#00ff00",
    orangered: "#FF4500",
    cinderella: "#fbd2d6",
    bizarre: "#f2dede",
    apple: "#5BA935",
    blueRomance: "#caf2d6",
    zumthor: "#E6ECFF",
    nero: "#222222",
    blackMarlin: "#3a3a3a",
    yellowOrange: "#fbb03b",
    scooter: "#46bfdb",
    nobel: "#b5b5b5",
    gray: "#888888",
    emperor: "#555555",
    arsenic: "#434343",
    woodCharcoal: "#464646",
};

const selectColors = {
    ...tintPrimary(colors.focusColor),
    neutral0: colors.secondaryBackground,
    neutral5: colors.secondaryColor,
    neutral10: colors.accent,
    neutral20: colors.mutedColor,
    neutral30: colors.borderColor,
    neutral40: colors.secondaryColor,
    neutral50: colors.mutedColor,
    neutral60: colors.mutedColor,
    neutral70: colors.secondaryColor,
    neutral80: colors.primaryColor,
    neutral90: colors.secondaryColor,
};

const custom = {
    borderRadius: 0,
    ConnectionErrorModal: {
        zIndex: 1600,
    },
    spacing: {
        controlHeight: 36,
        baseUnit: 4,
    },
    fontSize: 14,
    colors: {
        danger: "#DE350B",
        dangerLight: "#FFBDAD",
        warning: "#FF9A4D",
        error: "#f25c6e",
        ok: "#8fad60",
        success: "#64d864",
        info: "#b3b3b3",
        ...selectColors,
        ...colors,
    },
};

const globalStyles = {
    body: {
        fontFamily: "Open Sans, Helvetica Neue ,Helvetica,Arial,sans-serif",
    },
    "html, body": {
        margin: 0,
        padding: 0,
        height: "100dvh",
        background: "#b3b3b3",
        color: custom.colors.secondaryColor,
        fontSize: "16px",
        overflow: "hidden",
        letterSpacing: "unset",
        WebkitFontSmoothing: "initial",
        lineHeight: 1.428571429,
    },
    "input, button, select, textarea": {
        fontFamily: "inherit",
        fontSize: "inherit",
        lineHeight: "inherit",
    },
    " button,input,optgroup,select,textarea": {
        color: "inherit",
        font: "inherit",
        margin: 0,
    },

    button: {
        color: custom.colors.secondaryColor,
        lineHeight: 1.428571429,
        ":hover": {
            cursor: "pointer",
        },
    },
    p: {
        margin: "0 0 10px",
    },

    hr: {
        marginTop: "20px",
        marginBottom: "20px",
        border: 0,
        borderTop: `1px solid ${custom.colors.gallery}`,
    },
    "h1, h1, h3, h4, h5, h6": {
        fontFamily: "inherit",
        fontWeight: 500,
        lineHeight: 1.1,
        color: "inherit",
        marginTop: "20px",
        marginBottom: "10px",
    },

    a: {
        textDecoration: "none",
        ":hover": {
            textDecoration: "underline",
        },
    },
    "small, .small": {
        fontSize: "85%",
    },
    ".hide": {
        display: "none",
    },
    ".details": {
        display: "inline-block",
    },
    ".modalContentDark": {
        "& h3": {
            fontSize: "1.3em",
        },
    },
    ".ace_hidden-cursors .ace_cursor": {
        opacity: 0,
    },
    ".ace_editor.ace_autocomplete": {
        width: "400px",
    },
    /* Without those settings below, type (meta) shade method/variable name (value)*/
    ".ace_autocomplete .ace_line > *": {
        flex: "0 0 auto",
    },
    ".ace_autocomplete .ace_line .ace_": {
        flex: "0 0 auto",
        overflow: "auto",
    },
    ".ace_defaultMethod, .ace_defaultMethod + .ace_completion-meta": {
        color: "#ffe1b9",
    },
    ".ace_classMethod, .ace_classMethod + .ace_completion-meta": {
        color: "#708687",
    },
    ".ace_tooltip.ace_doc-tooltip": {
        fontSize: "0.7em",
        ".function-docs": {
            whiteSpace: "pre-wrap",
            "> hr": {
                marginTop: 0,
                marginBottom: 0,
            },

            "& p": {
                marginTop: "5px",
                marginBottom: "5px",
            },
        },
    },
    ".services": {
        height: "100%",
        overflowY: "auto",
    },

    ".notifications-wrapper": {
        position: "absolute",
        bottom: "25px",
        right: "25px",
        zIndex: 10000,
    },
    ".notification-dismiss": {
        display: "none",
    },
};

export const nuTheme = createTheme({
    palette: {
        mode: "dark",
        primary: {
            main: `#a9e074`,
        },
        secondary: {
            main: `#762976`,
        },
        error: {
            main: `#F25C6E`,
        },
        success: {
            main: `#5CB85C`,
            contrastText: `#FFFFFF`,
        },
        background: {
            paper: colors.primaryBackground,
            default: colors.canvasBackground,
        },
    },
    typography: {
        subtitle2: {
            fontSize: "12px",
            lineHeight: "inherit",
            color: custom.colors.baseColor,
        },
    },
    components: {
        MuiSwitch: {
            styleOverrides: {
                input: {
                    margin: 0,
                },
            },
        },
        MuiAlert: {
            styleOverrides: {
                root: {
                    width: 300,
                    zIndex: 20000,
                    marginTop: 10,
                    cursor: "pointer",
                    maxHeight: 400,
                    ".MuiAlert-icon": { color: custom.colors.secondaryBackground, alignSelf: "center" },
                },
                standardSuccess: {
                    backgroundColor: custom.colors.success,
                    color: custom.colors.secondaryBackground,
                },
                standardError: {
                    backgroundColor: custom.colors.error,
                    color: custom.colors.secondaryBackground,
                },
                standardWarning: {
                    backgroundColor: custom.colors.warning,
                },
                standardInfo: {
                    backgroundColor: custom.colors.secondaryColor,
                    color: custom.colors.secondaryBackground,
                },
            },
        },
        MuiCssBaseline: {
            styleOverrides: globalStyles,
        },
        MuiFormControl: {
            styleOverrides: {
                root: {
                    display: "flex",
                    flexDirection: "row",
                    margin: "16px 0",
                },
            },
        },
        MuiFormLabel: {
            styleOverrides: {
                root: {
                    display: "flex",
                    marginTop: "9px",
                    color: custom.colors.canvasBackground,
                    flexBasis: "20%",
                    maxWidth: "20em",
                    fontSize: "0.75rem",
                    fontWeight: 700,
                    overflowWrap: "anywhere",
                },
            },
            defaultProps: {
                focused: false,
            },
        },
        MuiFormHelperText: {
            styleOverrides: {
                root: {
                    marginLeft: 0,
                    color: custom.colors.success,
                },
            },
            variants: [{ props: { variant: "largeMessage" }, style: { fontSize: ".875rem" } }],
            defaultProps: {
                "data-testid": "form-helper-text",
            },
        },
    },
    custom,
});
