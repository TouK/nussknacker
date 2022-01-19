import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React, { PropsWithChildren, useEffect } from "react";
import { RootProviders } from "../settings";
import { useTheme } from "@emotion/react";
import { useDefaultTheme } from "./defaultTheme";
import { NavigationProvider } from "./parentNavigationProvider";

export interface NkViewProps {
    basepath?: string;
    onNavigate?: (path: string) => void;
}

export function NkView({ basepath, onNavigate, children }: PropsWithChildren<NkViewProps>): JSX.Element {
    const theme = useTheme();
    const defaultTheme = useDefaultTheme(theme);

    useEffect(() => {
        console.debug({ BUILD_HASH });
    }, []);

    return (
        <MuiThemeProvider theme={defaultTheme}>
            <NavigationProvider navigation={{ onNavigate }}>
                <RootProviders basepath={basepath}>{children}</RootProviders>
            </NavigationProvider>
        </MuiThemeProvider>
    );
}
