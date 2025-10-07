import { useTheme } from "@mui/material";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import type { RemoteComponentProps } from "nussknackerUi/containers/DynamicTab";
import type { PropsWithChildren } from "react";
import React, { useEffect, useMemo } from "react";

import { RootProviders } from "../settings/rootProviders";
import { useNuTheme } from "./nuTheme";
import { NavigationProvider } from "./parentNavigationProvider";
import { View } from "./view";

export type NkViewProps = Omit<RemoteComponentProps, "basepath">;

export function NkView(props: PropsWithChildren<NkViewProps>): JSX.Element {
    const theme = useTheme();
    const defaultTheme = useNuTheme(theme);
    const { navigate, children } = props;

    useEffect(() => {
        console.debug({ buildHash: BUILD_HASH, env: process.env.NODE_ENV });
    }, []);

    const navigation = useMemo(() => ({ onNavigate: navigate }), [navigate]);
    return (
        <MuiThemeProvider theme={defaultTheme}>
            <NavigationProvider navigation={navigation}>
                <RootProviders>
                    <View inTab>{children}</View>
                </RootProviders>
            </NavigationProvider>
        </MuiThemeProvider>
    );
}
