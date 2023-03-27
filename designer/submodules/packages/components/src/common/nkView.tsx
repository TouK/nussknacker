import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React, { PropsWithChildren, useEffect, useMemo } from "react";
import { RootProviders } from "../settings";
import { useTheme } from "@emotion/react";
import { useDefaultTheme } from "./defaultTheme";
import { NavigationProvider } from "./parentNavigationProvider";
import { RemoteComponentProps } from "nussknackerUi/containers/DynamicTab";
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
