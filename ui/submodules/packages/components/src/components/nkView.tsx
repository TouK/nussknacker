import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React, { useEffect } from "react";
import { RootProviders } from "../settings";
import { useTheme } from "@emotion/react";
import { useDefaultTheme } from "../defaultTheme";
import { RootRoutes } from "./rootRoutes";
import { NavigationProvider } from "./parentNavigationProvider";

export default function NkView(props: { basepath?: string; onNavigate?: (path: string) => void }): JSX.Element {
    const theme = useTheme();
    const defaultTheme = useDefaultTheme(theme);

    useEffect(() => {
        console.debug({ BUILD_HASH });
    }, []);

    const { onNavigate } = props;

    return (
        <MuiThemeProvider theme={defaultTheme}>
            <NavigationProvider navigation={{ onNavigate }}>
                <RootProviders basepath={props.basepath}>
                    <RootRoutes inTab />
                </RootProviders>
            </NavigationProvider>
        </MuiThemeProvider>
    );
}
