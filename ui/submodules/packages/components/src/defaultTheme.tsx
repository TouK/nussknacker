import { deepOrange, lightGreen } from "@mui/material/colors";
import { alpha, createTheme, Theme } from "@mui/material/styles";

const theme = createTheme({
    palette: {
        mode: "dark",
        primary: {
            main: lightGreen.A400,
        },
        secondary: {
            main: deepOrange["A400"],
        },
        background: {
            default: "#333333",
        },
    },
});

export const getDefaultTheme = (parent = {}): Theme => {
    const root = createTheme(theme, parent);
    const light = theme.palette.mode === "light";
    const bottomLineColor = light ? "rgba(0, 0, 0, 0.42)" : "rgba(255, 255, 255, 0.42)";
    const backgroundColor = light ? "rgba(0, 0, 0, 0.06)" : "rgba(0, 0, 0, 0.25)";
    return createTheme(root, {
        components: {
            MuiDataGrid: {
                styleOverrides: {
                    root: {
                        border: 0,
                    },
                    row: {
                        ":nth-of-type(even):not(:hover)": {
                            backgroundColor: alpha(root.palette.action.hover, root.palette.action.hoverOpacity * 1.5),
                        },
                    },
                    columnHeader: {
                        backgroundColor: root.palette.augmentColor({ color: { main: root.palette.background.paper } }).dark,
                    },
                    "cell--withRenderer": {
                        "&.withLink": {
                            padding: 0,
                            alignItems: "stretch",
                        },
                    },
                },
            },
            MuiFilledInput: {
                styleOverrides: {
                    root: {
                        backgroundColor,
                        ":before": {
                            borderBottomColor: bottomLineColor,
                        },
                    },
                },
            },
        },
    });
};

export const defaultTheme = getDefaultTheme();
