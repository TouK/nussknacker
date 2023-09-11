import { createTheme, Theme as MuiTheme } from "@mui/material/styles";
import { defaultsDeep } from "lodash";
import { useMemo } from "react";
import { useNkTheme } from "./theme";
import { Theme } from "@emotion/react";
import { variables } from "../stylesheets/variables";

declare module "@mui/material/styles" {
    interface Theme {
        custom: {
            ConnectionErrorModal: {
                zIndex: number;
            };
        };
    }
    // allow configuration using `createTheme`
    interface ThemeOptions {
        custom?: {
            ConnectionErrorModal?: {
                zIndex: number;
            };
        };
    }
}

// translate emotion (nk) theme to mui theme
export function useMuiTheme(): MuiTheme & Theme {
    const { theme } = useNkTheme();

    const isDark = useMemo(() => theme.themeClass.toLowerCase().includes("dark"), [theme.themeClass]);

    return useMemo(
        () =>
            defaultsDeep(
                createTheme(
                    createTheme({
                        palette: {
                            mode: isDark ? "dark" : "light",
                            primary: {
                                main: `#a9e074`,
                            },
                            secondary: {
                                main: `#762976`,
                            },
                            error: {
                                main: `#F25C6E`,
                            },
                            success: {
                                main: `#5CB85C`,
                                contrastText: `#FFFFFF`,
                            },
                            background: {
                                paper: theme.colors.primaryBackground,
                                default: theme.colors.canvasBackground,
                            },
                        },
                        components: {
                            MuiSwitch: {
                                styleOverrides: {
                                    input: {
                                        margin: 0,
                                    },
                                },
                            },
                            MuiAlert: {
                                styleOverrides: {
                                    standardSuccess: {
                                        backgroundColor: variables.alert.sucsess,
                                        color: "#333333",
                                    },
                                    standardError: {
                                        backgroundColor: variables.alert.error,
                                        color: "#333333",
                                    },
                                    standardWarning: {
                                        backgroundColor: variables.alert.warning,
                                    },
                                    standardInfo: {
                                        backgroundColor: variables.alert.info,
                                        color: "#333333",
                                    },
                                },
                            },
                        },
                        custom: {
                            ConnectionErrorModal: {
                                zIndex: 1600,
                            },
                        },
                    }),
                ),
                theme,
            ),
        [isDark, theme],
    );
}
