import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { View } from "./view";
import { RootProviders } from "../settings";
import { useTheme } from "@emotion/react";
import { Box } from "@mui/material";
import { getDefaultTheme } from "../defaultTheme";

export default function NkView(props: { basepath?: string }): JSX.Element {
    const theme = useTheme();
    return (
        <MuiThemeProvider theme={getDefaultTheme(theme)}>
            <RootProviders>
                <Box
                    sx={{
                        display: "flex",
                        flexDirection: "row",
                        minHeight: "100%",
                        maxHeight: "100%",
                        minWidth: "100%",
                        maxWidth: "100%",
                    }}
                >
                    <View basepath={props.basepath} />
                </Box>
            </RootProviders>
        </MuiThemeProvider>
    );
}
