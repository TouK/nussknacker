import { ThemeProvider } from "@mui/material/styles";
import React, { PropsWithChildren } from "react";
import { GlobalCSSVariables } from "./helpers";
import { nuTheme } from "./nuTheme";

export const NuThemeProvider: React.FC<PropsWithChildren<unknown>> = ({ children }) => {
    return (
        <ThemeProvider theme={nuTheme}>
            <GlobalCSSVariables />
            {children}
        </ThemeProvider>
    );
};
