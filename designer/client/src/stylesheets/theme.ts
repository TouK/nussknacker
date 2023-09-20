import { Color } from "@mui/material";
import { createTheme } from "@mui/material/styles";

declare module "@mui/material/styles" {
    interface Palette {
        dark?: Partial<Color>;
        blue?: Partial<Color>;
        green?: Partial<Color>;
    }
    interface PaletteOptions {
        dark?: Partial<Color>;
        blue?: Partial<Color>;
        green?: Partial<Color>;
    }
}

let newTheme = createTheme({
    palette: {
        grey: {
            50: "#F5F5F5",
            100: "#E0E0E0",
            200: "#b3b3b3",
            300: "#777",
            400: "#666",
            500: "#4d4d4d",
            600: "#434343",
            700: "#333",
            800: "#3e3e3e",
            900: "#222",
            A100: "#f6f6f6",
            A200: "#999999",
            A400: "#808080",
            A700: "#616161",
        },
        blue: {
            200: "#0E9AE0",
            500: "#0058a9",
        },
        green: {
            200: "8fad60",
        },
        dark: {
            200: "#afafaf",
            300: "#444",
            500: "#ccc",
            600: "#5d5d5d",
            700: "#3a3a3a",
        },
    },
});

newTheme = createTheme(newTheme, {
    palette: {
        mode: "dark",
        primary: {
            main: newTheme.palette.blue[500], // Primary color
        },
        info: {
            main: newTheme.palette.dark[500], // Info color
        },
        warning: {
            main: "#FF9A4D", // Warning color
        },
        error: {
            main: "#f25c6e", // Error color
        },
        success: {
            main: "#64d864", // Success color
        },
    },
});
