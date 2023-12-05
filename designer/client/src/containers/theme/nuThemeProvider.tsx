import { ThemeProvider } from "@mui/material/styles";
import React, { PropsWithChildren } from "react";
import { nuTheme } from "./nuTheme";
import { CssBaseline } from "@mui/material";

export const NuThemeProvider: React.FC<PropsWithChildren<unknown>> = ({ children }) => {
    return (
        <ThemeProvider theme={nuTheme}>
            <CssBaseline />
            {children}
        </ThemeProvider>
    );
};
