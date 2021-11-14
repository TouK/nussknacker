import { createTheme, ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import React from "react";
import { View } from "./view";
import { RootProviders } from "../settings";
import { useTheme } from "@emotion/react";
import { Box } from "@mui/material";
import { defaultTheme } from "../defaultTheme";

export default function NkTab(): JSX.Element {
    const theme = useTheme();
    return (
        <RootProviders>
            <MuiThemeProvider theme={createTheme(defaultTheme, theme)}>
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
                    <View />
                </Box>
            </MuiThemeProvider>
        </RootProviders>
    );
}
