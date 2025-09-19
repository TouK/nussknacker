import { useTheme } from "@mui/material";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import type { RemoteComponentProps } from "nussknackerUi/containers/DynamicTab";
import type { PropsWithChildren } from "react";
import React, { useEffect, useMemo } from "react";

import { RootProviders } from "../settings/rootProviders";
import { useDefaultTheme } from "./defaultTheme";
import { NavigationProvider } from "./parentNavigationProvider";
import { View } from "./view";

export type NkViewProps = Omit<RemoteComponentProps, "basepath">;

export function NkView(props: PropsWithChildren<NkViewProps>): JSX.Element {
    const theme = useTheme();
    const defaultTheme = useDefaultTheme(theme);
    const { navigate, children } = props;

    useEffect(() => {
        console.debug({ BUILD_HASH });
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
