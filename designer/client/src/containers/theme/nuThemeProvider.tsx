import { ThemeProvider } from "@mui/material/styles";
import React, { PropsWithChildren } from "react";
import { nuTheme } from "./nuTheme";
import { CssBaseline, PaletteMode } from "@mui/material";
import { useLocalstorageState } from "rooks";

export const NuThemeProvider: React.FC<PropsWithChildren<unknown>> = ({ children }) => {
    const [mode, setMode] = useLocalstorageState<PaletteMode>("palette-mode", "dark");

    const theme = React.useMemo(() => nuTheme(mode, setMode), [mode, setMode]);

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />
            {children}
        </ThemeProvider>
    );
};
