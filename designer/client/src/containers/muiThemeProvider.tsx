import { ThemeProvider } from "@mui/material/styles";
import React, { PropsWithChildren } from "react";
import { useMuiTheme } from "./useMuiTheme";

export const MuiThemeProvider: React.FC<PropsWithChildren> = ({ children }) => {
    const muiTheme = useMuiTheme();

    return <ThemeProvider theme={muiTheme}>{children}</ThemeProvider>;
};
