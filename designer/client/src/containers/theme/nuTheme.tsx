import { tintPrimary } from "./helpers";
import { createTheme } from "@mui/material";

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
    evenBackground: d3,
    accent: "#668547",
    mineShaft: "#3e3e3e",
    tundora: d3,
    scorpion: "#5D5D5D",
    silverChalice: "#afafaf",
    cerulean: "#0E9AE0",
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
        controlHeight: 36,
        baseUnit: 4,
    },
    fontSize: 14,
    colors: {
        danger: "#DE350B",
        dangerLight: "#FFBDAD",
        accent: "#0058a9",
        warning: "#FF9A4D",
        error: "#f25c6e",
        ok: "#8fad60",
        success: "#64d864",
        info: "#b3b3b3",
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
    },
    custom,
});
