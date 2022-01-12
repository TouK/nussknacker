import { CssBaseline } from "@mui/material";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { RootProvidersWithAuth } from "./settings";
import { useDefaultTheme } from "./defaultTheme";
import { RootRoutes } from "./components/rootRoutes";

export const Root = (): JSX.Element => {
    const defaultTheme = useDefaultTheme();
    return (
        <MuiThemeProvider theme={defaultTheme}>
            <CssBaseline />
            <RootProvidersWithAuth>
                <RootRoutes />
            </RootProvidersWithAuth>
        </MuiThemeProvider>
    );
};
