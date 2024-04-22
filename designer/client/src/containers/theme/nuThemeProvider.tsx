import { ThemeProvider } from "@mui/material/styles";
import React, { PropsWithChildren } from "react";
import { nuTheme } from "./nuTheme";
import { CssBaseline, PaletteMode } from "@mui/material";

export const NuThemeProvider: React.FC<PropsWithChildren<unknown>> = ({ children }) => {
    const [mode, setMode] = React.useState<PaletteMode>("dark");

    const theme = React.useMemo(() => nuTheme(mode), [mode]);

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />
            {children}
        </ThemeProvider>
    );
};
