import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { Demo } from "../demo";
import { DefaultProviders } from "./defaultProviders";
import { useTheme } from "@emotion/react";
import { NkTheme } from "nussknackerUi/containers/theme";
import { Box } from "@mui/material";

const TabRoot = (): JSX.Element => {
    return (
        <DefaultProviders>
            <Box
                sx={{
                    display: "flex",
                    flexDirection: "row",
                    minHeight: "100%",
                    maxHeight: "100%",
                    minWidth: "100%",
                    maxWidth: "100%",
                }}
            >
                <Demo />
            </Box>
        </DefaultProviders>
    );
};

export default function NkTab(): JSX.Element {
    const theme = useTheme();
    return (
        <MuiThemeProvider theme={theme}>
            <TabRoot />
        </MuiThemeProvider>
    );
}
