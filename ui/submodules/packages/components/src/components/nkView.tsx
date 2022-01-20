import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React, { useEffect } from "react";
import { RootProviders } from "../settings";
import { useTheme } from "@emotion/react";
import { useDefaultTheme } from "../defaultTheme";
import { RootRoutes } from "./rootRoutes";

export default function NkView(props: { basepath?: string }): JSX.Element {
    const theme = useTheme();
    const defaultTheme = useDefaultTheme(theme);

    useEffect(() => {
        console.debug({ BUILD_HASH });
    }, []);

    return (
        <MuiThemeProvider theme={defaultTheme}>
            <RootProviders basepath={props.basepath}>
                <RootRoutes inTab />
            </RootProviders>
        </MuiThemeProvider>
    );
}
