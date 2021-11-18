import { Box, CssBaseline } from "@mui/material";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { View } from "./components";
import { RootProvidersWithAuth } from "./settings";
import { defaultTheme } from "./defaultTheme";

export const Root = (): JSX.Element => {
    return (
        <RootProvidersWithAuth>
            <MuiThemeProvider theme={defaultTheme}>
                <CssBaseline />
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
            </MuiThemeProvider>
        </RootProvidersWithAuth>
    );
};
