import { deepPurple, lightGreen } from "@mui/material/colors";
import { alpha, createTheme, Theme } from "@mui/material/styles";

const theme = createTheme({
    palette: {
        mode: "dark",
        primary: {
            main: lightGreen.A400,
        },
        secondary: {
            main: deepPurple["900"],
        },
        background: {
            default: "#333333",
        },
    },
});

export const getDefaultTheme = (parent = {}): Theme => {
    const root = createTheme(theme, parent);
    return createTheme(root, {
        components: {
            MuiDataGrid: {
                styleOverrides: {
                    root: {
                        border: 0,
                    },
                    row: {
                        ":nth-of-type(even):not(:hover)": {
                            backgroundColor: alpha(root.palette.action.hover, root.palette.action.hoverOpacity / 2),
                        },
                    },
                    columnHeader: {
                        backgroundColor: root.palette.background.paper,
                    },
                },
            },
        },
    });
};

export const defaultTheme = getDefaultTheme();
