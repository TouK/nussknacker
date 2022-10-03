import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React, { PropsWithChildren, useEffect, useMemo } from "react";
import { RootProviders } from "../settings";
import { useTheme } from "@emotion/react";
import { useDefaultTheme } from "./defaultTheme";
import { NavigationProvider } from "./parentNavigationProvider";

export interface NkViewProps {
    basepath?: string;
    onNavigate?: (path: string) => void;
}

export function NkView(props: PropsWithChildren<NkViewProps>): JSX.Element {
    const theme = useTheme();
    const defaultTheme = useDefaultTheme(theme);
    const { basepath, onNavigate, children } = props;

    useEffect(() => {
        console.debug({ BUILD_HASH });
    }, []);

    const navigation = useMemo(() => ({ onNavigate }), [onNavigate]);
    return (
        <MuiThemeProvider theme={defaultTheme}>
            <NavigationProvider navigation={navigation}>
                <RootProviders basepath={basepath}>{children}</RootProviders>
            </NavigationProvider>
        </MuiThemeProvider>
    );
}
