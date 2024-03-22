import { createTheme, rgbToHex } from "@mui/material";
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
            main: `#762976`,
        },
        error: {
            main: `#F25C6E`,
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
            hover: blendLighten("#242F3E", 0.15),
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
                    ".MuiAlert-icon": { color: theme.palette.background.paper, alignSelf: "center" },
                }),
                standardSuccess: ({ theme }) => ({
                    backgroundColor: theme.palette.success.main,
                    color: theme.palette.text.secondary,
                }),
                standardError: ({ theme }) => ({
                    backgroundColor: theme.palette.error.main,
                    color: theme.palette.text.secondary,
                }),
                standardWarning: ({ theme }) => ({
                    backgroundColor: theme.palette.warning.main,
                }),
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
