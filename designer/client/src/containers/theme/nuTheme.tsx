import { alpha, createTheme, rgbToHex } from "@mui/material";
import { blend } from "@mui/system";
import { fontFamily, globalStyles } from "./styles";

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

const colors = {
    eucalyptus: "#33A369",
    seaGarden: "#2D8E54",
    lawnGreen: "#7EDB0D",
    red: "#FF0000",
    yellow: "#ffff00",
    deepskyblue: "#00bfff",
    lime: "#00ff00",
    orangered: "#FF4500",
    bizarre: "#f2dede",
    apple: "#5BA935",
    blueRomance: "#caf2d6",
    zumthor: "#E6ECFF",
    nero: "#222222",
    blackMarlin: "#3a3a3a",
    yellowOrange: "#fbb03b",
    scooter: "#46bfdb",
    nobel: "#b5b5b5",
    sources: {
        eucalyptus: "#33A369",
        seaGarden: "#2D8E54",
    },
    split: {},
    filter: {},
    custom: {},
    enrichers: {},
    choice: {},
    sinks: {},
    variable: {},
    fragments: {},
};

const custom = {
    ConnectionErrorModal: {
        zIndex: 1600,
    },
    spacing: {
        controlHeight: 36,
        baseUnit: 4,
    },
    fontSize: 14,
    colors: {
        ...colors,
    },
};

const headerCommonStyles = {
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
            light: "#D2A8FF",
            main: `#762976`,
        },
        error: {
            main: `#D4354D`,
        },
        warning: {
            main: "#FF9A4D",
        },
        success: {
            main: `#80D880`,
            dark: `#206920`,
            contrastText: `#FFFFFF`,
        },
        background: {
            paper: "#242F3E",
            default: "#131A25",
        },
        text: {
            primary: "#ededed",
            secondary: "#cccccc",
        },
        action: {
            hover: alpha("#D2A8FF", 0.24),
        },
    },
    typography: (palette) => ({
        fontFamily,
        h1: { ...headerCommonStyles },
        h2: { ...headerCommonStyles },
        h3: { ...headerCommonStyles },
        h4: { ...headerCommonStyles },
        h5: { ...headerCommonStyles },
        h6: { ...headerCommonStyles },
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
                    ".MuiAlert-icon": { color: alpha(theme.palette.common.black, 0.54), alignSelf: "center" },
                }),
                standardSuccess: ({ theme }) => ({
                    backgroundColor: theme.palette.success.main,
                    color: blendDarken(theme.palette.text.primary, 0.9),
                }),
                standardError: ({ theme }) => ({
                    backgroundColor: blendLighten(theme.palette.error.main, 0.15),
                    color: blendDarken(theme.palette.text.primary, 0.9),
                }),
                standardWarning: ({ theme }) => ({
                    backgroundColor: theme.palette.warning.main,
                }),
                standardInfo: ({ theme }) => ({
                    backgroundColor: theme.palette.primary.main,
                    color: blendDarken(theme.palette.text.primary, 0.9),
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
                root: ({ theme }) => ({
                    marginLeft: 0,
                    color: theme.palette.success.main,
                }),
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
