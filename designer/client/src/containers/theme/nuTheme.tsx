import { tintPrimary } from "./helpers";
import { createTheme, rgbToHex, Theme } from "@mui/material";
import { blend } from "@mui/system";

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

export const blendDarken = (color: string, opacity: number) => rgbToHex(blend(color, "#000000", opacity));
export const blendLighten = (color: string, opacity: number) => rgbToHex(blend(color, "#ffffff", opacity));

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
    primaryColor: l,
    secondaryColor: l2,
    mutedColor: base,
    focusColor: d1,
    baseColor: l4,
    tundora: d3,
    doveGray: d4,
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
};

const selectColors = {
    ...tintPrimary(colors.focusColor),
    neutral0: "242F3E",
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

const fontFamily = [
    "Inter",
    "-apple-system",
    "BlinkMacSystemFont",
    '"Segoe UI"',
    '"Helvetica Neue"',
    "Arial",
    "sans-serif",
    '"Apple Color Emoji"',
    '"Segoe UI Emoji"',
    '"Segoe UI Symbol"',
].join(",");

const globalStyles = (theme: Theme) => ({
    "html, body": {
        margin: 0,
        padding: 0,
        height: "100dvh",
        color: custom.colors.secondaryColor,
        fontSize: "16px",
        overflow: "hidden",
        letterSpacing: "unset",
        WebkitFontSmoothing: "initial",
        lineHeight: 1.428571429,
        fontFamily,
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

    hr: {
        marginTop: "20px",
        marginBottom: "20px",
        border: 0,
        borderTop: `1px solid ${custom.colors.gallery}`,
    },

    a: {
        textDecoration: "none",
        ":hover": {
            textDecoration: "underline",
        },
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
        whiteSpace: "pre",
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

    // Styles joint-js elements
    "#nk-graph-main text": {
        ...theme.typography.body1,
    },
});

const headerCommonStyle = {
    fontWeight: 500,
    lineHeight: 1.1,
    marginTop: "20px",
    marginBottom: "10px",
};

export const nuTheme = createTheme({
    palette: {
        mode: "dark",
        primary: {
            main: `#D2A8FF`,
        },
        secondary: {
            main: `#762976`,
        },
        error: {
            main: `#F25C6E`,
        },
        success: {
            main: `#668547`,
            contrastText: `#FFFFFF`,
        },
        background: {
            paper: "#242F3E",
            default: "#131A25",
        },
        action: {
            hover: blendLighten("#242F3E", 0.15),
        },
    },
    typography: (palette) => ({
        fontFamily,
        h1: { ...headerCommonStyle },
        h2: { ...headerCommonStyle },
        h3: { ...headerCommonStyle },
        h4: { ...headerCommonStyle },
        h5: { ...headerCommonStyle },
        h6: { ...headerCommonStyle },
        subtitle1: {
            fontWeight: "bold",
        },
        subtitle2: {
            fontWeight: "bold",
        },
        overline: {
            fontSize: ".6875rem",
            letterSpacing: "inherit",
            lineHeight: "inherit",
            textTransform: "inherit",
            color: palette.text.secondary,
        },
    }),
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
                root: ({ theme }) => ({
                    width: 300,
                    zIndex: 20000,
                    marginTop: 10,
                    cursor: "pointer",
                    maxHeight: 400,
                    ".MuiAlert-icon": { color: theme.palette.background.paper, alignSelf: "center" },
                }),
                standardSuccess: ({ theme }) => ({
                    backgroundColor: custom.colors.success,
                    color: theme.palette.text.secondary,
                }),
                standardError: ({ theme }) => ({
                    backgroundColor: custom.colors.error,
                    color: theme.palette.text.secondary,
                }),
                standardWarning: {
                    backgroundColor: custom.colors.warning,
                },
                standardInfo: ({ theme }) => ({
                    backgroundColor: theme.palette.info.light,
                    color: theme.palette.text.secondary,
                }),
            },
        },
        MuiCssBaseline: {
            styleOverrides: (theme) => globalStyles(theme),
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
                root: ({ theme }) => ({
                    ...theme.typography.body2,
                    display: "flex",
                    marginTop: "9px",
                    flexBasis: "20%",
                    maxWidth: "20em",
                    overflowWrap: "anywhere",
                }),
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
        MuiAutocomplete: {
            styleOverrides: {
                noOptions: ({ theme }) => ({
                    ...theme.typography.body2,
                    padding: theme.spacing(0.75, 2),
                    marginTop: theme.spacing(0.5),
                    backgroundColor: theme.palette.background.paper,
                }),
                loading: ({ theme }) => ({
                    ...theme.typography.body2,
                    padding: theme.spacing(0.75, 2),
                    marginTop: theme.spacing(0.5),
                    backgroundColor: theme.palette.background.paper,
                }),
            },
        },
    },
    custom,
});
