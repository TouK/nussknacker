import { Box, CssBaseline } from "@mui/material";
import { deepPurple, lightGreen } from "@mui/material/colors";
import { createTheme, ThemeProvider } from "@mui/material/styles";
import React from "react";
import { Demo } from "./demo";
import { DefaultProviders } from "./scenarios/defaultProviders";
import { Auth } from "./settings";

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

const App = () => {
    return (
        <ThemeProvider theme={theme}>
            <Box
                sx={{
                    display: "flex",
                    flexDirection: "row",
                    minHeight: "100vh",
                    maxHeight: "100vh",
                }}
            >
                <Auth>
                    <Demo />
                </Auth>
            </Box>
        </ThemeProvider>
    );
};

const defaultTheme = createTheme();

export const Root = (): JSX.Element => {
    return (
        <>
            <ThemeProvider theme={defaultTheme}>
                <CssBaseline />
                <DefaultProviders>
                    <App />
                </DefaultProviders>
            </ThemeProvider>
        </>
    );
};
