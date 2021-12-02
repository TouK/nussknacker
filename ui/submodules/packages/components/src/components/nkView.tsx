import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { RootProviders } from "../settings";
import { useTheme } from "@emotion/react";
import { getDefaultTheme } from "../defaultTheme";
import { RootRoutes } from "./rootRoutes";

export default function NkView(props: { basepath?: string }): JSX.Element {
    const theme = useTheme();
    return (
        <MuiThemeProvider theme={getDefaultTheme(theme)}>
            <RootProviders basepath={props.basepath}>
                <RootRoutes inTab />
            </RootProviders>
        </MuiThemeProvider>
    );
}
