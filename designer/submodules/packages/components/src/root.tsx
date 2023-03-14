import { CssBaseline } from "@mui/material";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { RootProvidersWithAuth } from "./settings";
import { useDefaultTheme, View } from "./common";
import { RootRoutes } from "./components/rootRoutes";
import { BrowserRouter } from "react-router-dom";
import { Navigation } from "./navigation";

export const Root = (): JSX.Element => {
    const defaultTheme = useDefaultTheme();
    return (
        <MuiThemeProvider theme={defaultTheme}>
            <CssBaseline />
            <BrowserRouter>
                <RootProvidersWithAuth>
                    <View>
                        <Navigation />
                        <RootRoutes />
                    </View>
                </RootProvidersWithAuth>
            </BrowserRouter>
        </MuiThemeProvider>
    );
};
