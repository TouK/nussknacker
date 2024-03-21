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
    secondaryColor: l2,
    mutedColor: base,
    focusColor: d1,
    baseColor: l4,
    tundora: d3,
    accent: "#668547",
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
    emperor: "#555555",
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

const aceEditor = (theme: Theme) => ({
    ".ace-nussknacker .ace_gutter": {
        background: blendLighten(theme.palette.background.paper, 0.1),
        color: theme.palette.text.secondary,
    },
    ".ace-nussknacker.ace_editor": {
        fontFamily: "'Roboto Mono', 'Monaco', monospace",
    },
    ".ace-nussknacker .ace_print-margin": {
        width: "1px",
        background: "red",
    },

    ".ace-nussknacker .ace_search": {
        background: "#818181",
    },

    ".ace-nussknacker .ace_search_field": {
        color: "#ccc",
    },

    ".ace-nussknacker": {
        backgroundColor: theme.palette.background.paper,
        color: theme.palette.text.secondary,
        outline: `1px solid ${blendLighten(theme.palette.background.paper, 0.25)}`,
        "&:focus-within": {
            outline: `1px solid ${theme.palette.primary.main}`,
        },
    },
    ".ace-nussknacker .ace_cursor": {
        color: "#F8F8F0",
    },
    ".ace-nussknacker .ace_marker-layer .ace_selection": {
        background: "#49483E",
    },
    ".ace-nussknacker.ace_multiselect .ace_selection.ace_start": {
        boxShadow: "0 0 3px 0px #333",
    },
    ".ace-nussknacker .ace_marker-layer .ace_step": {
        background: "rgb(102, 82, 0)",
    },
    ".ace-nussknacker .ace_marker-layer .ace_bracket": {
        margin: 0,
        border: "1px solid #FFC66D",
    },
    ".ace-nussknacker .ace_marker-layer .ace_active-line": {
        background: blendDarken(theme.palette.background.paper, 0.2),
    },
    ".ace-nussknacker .ace_gutter-active-line": {
        backgroundColor: blendDarken(theme.palette.background.paper, 0.2),
    },
    ".ace-nussknacker .ace_marker-layer .ace_selected-word": {
        border: "1px solid #49483E",
    },
    ".ace-nussknacker .ace_invisible": {
        color: "#52524d",
    },
    ".ace-nussknacker .ace_entity.ace_name.ace_tag, .ace-nussknacker .ace_keyword, .ace-nussknacker .ace_meta.ace_tag, .ace-nussknacker .ace_storage":
        {
            color: "#db7e3a",
        },
    ".ace-nussknacker .ace_punctuation, .ace-nussknacker .ace_punctuation.ace_tag": {
        color: "#fff",
    },
    ".ace-nussknacker .ace_constant.ace_character, .ace-nussknacker .ace_constant.ace_language, .ace-nussknacker .ace_constant.ace_numeric, .ace-nussknacker .ace_constant.ace_other":
        {
            color: "#AE81FF",
        },
    ".ace-nussknacker .ace_invalid": {
        color: "#F8F8F0",
        backgroundColor: "#F92672",
    },
    ".ace-nussknacker .ace_invalid.ace_deprecated": {
        color: "#F8F8F0",
        backgroundColor: "#AE81FF",
    },
    ".ace-nussknacker .ace_support.ace_constant, .ace-nussknacker .ace_support.ace_function": {
        color: "#f3b560",
    },
    ".ace-nussknacker .ace_fold": {
        backgroundColor: "#A6E22E",
        borderColor: "#F8F8F2",
    },
    ".ace-nussknacker .ace_storage.ace_type, .ace-nussknacker .ace_support.ace_class, .ace-nussknacker .ace_support.ace_type": {
        fontStyle: "italic",
        color: "#f3b560",
    },
    ".ace-nussknacker .ace_entity.ace_name.ace_function, .ace-nussknacker .ace_entity.ace_other, .ace-nussknacker .ace_entity.ace_other.ace_attribute-name, .ace-nussknacker .ace_variable":
        {
            color: "#9876AA",
        },
    ".ace-nussknacker .ace_variable.ace_parameter": {
        fontStyle: "italic",
        color: "#FD971F",
    },
    ".ace-nussknacker .ace_string": {
        color: "#6A8759",
    },
    ".ace-nussknacker .ace_comment": {
        color: "#75715E",
    },
    ".ace-nussknacker .ace_spel": {
        color: "#337AB7",
    },
    ".ace-nussknacker .ace_paren": {
        fontWeight: "bold",
    },
    ".ace-nussknacker .ace_alias": {
        color: "#37CB86",
    },
    ".ace-nussknacker .ace_indent-guide": {
        background:
            "url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAACCAYAAACZgbYnAAAAEklEQVQImWPQ0FD0ZXBzd/wPAAjVAoxeSgNeAAAAAElFTkSuQmCC) right repeat-y",
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
});

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
    "input, button, select, textarea, .row-ace-editor": {
        fontFamily: "inherit",
        fontSize: "inherit",
        lineHeight: "inherit",
        boxShadow: "none",
        border: "none",
        outline: `1px solid ${blendLighten(theme.palette.background.paper, 0.25)}`,
        "&:focus, &:focus-within": {
            outline: `1px solid ${theme.palette.primary.main}`,
        },
    },

    "input[readonly], select[readonly], textarea[readonly], input[type='checkbox'][readonly]:after, input[type='radio'][readonly]:after, .row-ace-editor[readonly]":
        {
            backgroundColor: `${theme.palette.action.disabledBackground} !important`,
            color: `${theme.palette.action.disabled} !important`,
        },
    "input[disabled], select[disabled], textarea[disabled], input[type='checkbox'][disabled]:after, input[type='radio'][disabled]:after, .row-ace-editor[disabled]":
        {
            backgroundColor: `${theme.palette.action.disabledBackground} !important`,
            color: `${theme.palette.action.disabled} !important`,
        },
    " button,input,optgroup,select,textarea": {
        color: "inherit",
        font: "inherit",
        margin: 0,
    },

    button: {
        color: custom.colors.secondaryColor,
        lineHeight: 1.428571429,
        outline: `1px solid ${blendLighten(theme.palette.background.paper, 0.25)}`,
        ":hover": {
            cursor: "pointer",
            backgroundColor: theme.palette.action.hover,
        },
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
    ...aceEditor(theme),
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
