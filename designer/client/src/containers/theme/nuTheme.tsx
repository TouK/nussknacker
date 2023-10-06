import { variables } from "../../stylesheets/variables";
import { tintPrimary } from "./helpers";
import { createTheme } from "@mui/material";

declare module "@mui/material/styles" {
    // eslint-disable-next-line @typescript-eslint/no-empty-interface
    interface Theme {
        custom: typeof custom;
    }
    // eslint-disable-next-line @typescript-eslint/no-empty-interface
    interface ThemeOptions {
        custom: typeof custom;
    }
}

const { primary, warningColor: warning, errorColor: error, okColor: ok, success, formControlHeight } = variables;

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
    evenBackground: d3,
    selectedValue: d2,
    accent: "#668547",
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
    themeClass: "",
    borderRadius: 0,
    ConnectionErrorModal: {
        zIndex: 1600,
    },
    spacing: {
        controlHeight: parseFloat(formControlHeight),
        baseUnit: 4,
    },
    fontSize: 14,
    colors: {
        danger: "#DE350B",
        dangerLight: "#FFBDAD",
        selectedValue: primary,
        accent: primary,
        warning,
        error,
        ok,
        success,
        warn: warning,
        info: primary,
        ...selectColors,
        ...colors,
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
                    ".MuiAlert-icon": { color: variables.alert.text, alignSelf: "center" },
                },
                standardSuccess: {
                    backgroundColor: variables.success,
                    color: variables.alert.text,
                },
                standardError: {
                    backgroundColor: variables.alert.error,
                    color: variables.alert.text,
                },
                standardWarning: {
                    backgroundColor: variables.alert.warning,
                },
                standardInfo: {
                    backgroundColor: variables.alert.info,
                    color: variables.alert.text,
                },
            },
        },
    },
    custom,
});
