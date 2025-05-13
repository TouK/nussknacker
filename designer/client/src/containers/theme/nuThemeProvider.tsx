import type { PaletteMode } from "@mui/material";
import { CssBaseline } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import type { PropsWithChildren } from "react";
import React from "react";
import { useLocalstorageState } from "rooks";

import { nuTheme } from "./nuTheme";

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
