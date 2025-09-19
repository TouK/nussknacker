import { CssBaseline, GlobalStyles } from "@mui/material";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { BrowserRouter } from "react-router-dom";

import { useDefaultTheme } from "./common/defaultTheme";
import { View } from "./common/view";
import { RootRoutes } from "./components/rootRoutes";
import { Navigation } from "./navigation";
import { RootProvidersWithAuth } from "./settings/rootProvidersWithAuth";

export const Root = (): JSX.Element => {
    const defaultTheme = useDefaultTheme();
    return (
        <MuiThemeProvider theme={defaultTheme}>
            <CssBaseline />
            <GlobalStyles
                styles={(theme) => ({
                    ":root": {
                        "--warnColor": theme.palette.warning.main,
                        "--errorColor": theme.palette.error.main,
                        "--successColor": theme.palette.success.main,
                        "--infoColor": theme.palette.info.main,
                    },
                })}
            />
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
