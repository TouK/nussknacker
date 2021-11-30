import { Box, CssBaseline } from "@mui/material";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { View } from "./components";
import { RootProvidersWithAuth } from "./settings";
import { defaultTheme } from "./defaultTheme";

export const Root = (): JSX.Element => {
    return (
        <MuiThemeProvider theme={defaultTheme}>
            <CssBaseline />
            <RootProvidersWithAuth>
                <Box
                    sx={{
                        display: "flex",
                        flexDirection: "row",
                        minHeight: "100vh",
                        maxHeight: "100vh",
                    }}
                >
                    <View />
                </Box>
            </RootProvidersWithAuth>
        </MuiThemeProvider>
    );
};
