import { deepPurple, lightGreen } from "@mui/material/colors";
import { createTheme } from "@mui/material/styles";
import type {} from "@mui/x-data-grid/themeAugmentation";

export const defaultTheme = createTheme({
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
    components: {
        MuiDataGrid: {
            styleOverrides: {
                root: {
                    border: "none",
                },
            },
        },
    },
});
