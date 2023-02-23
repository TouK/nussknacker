import { CssBaseline } from "@mui/material";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { RootProvidersWithAuth } from "./settings";
import { useDefaultTheme, View } from "./common";
import { RootRoutes } from "./components/rootRoutes";
import { BrowserRouter } from "react-router-dom";

export const Root = (): JSX.Element => {
    const defaultTheme = useDefaultTheme();
    return (
        <MuiThemeProvider theme={defaultTheme}>
            <CssBaseline />
            <BrowserRouter>
                <RootProvidersWithAuth>
                    <View>
                        <RootRoutes />
                    </View>
                </RootProvidersWithAuth>
            </BrowserRouter>
        </MuiThemeProvider>
    );
};
