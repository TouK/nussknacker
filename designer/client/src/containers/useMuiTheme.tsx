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
                            MuiAlert: {
                                styleOverrides: {
                                    root: {
                                        width: 300,
                                        zIndex: 20000,
                                        marginTop: 10,
                                        cursor: "pointer",
                                        maxHeight: 400,
                                        ".MuiAlert-icon": { color: variables.alert.text, alignSelf: "center" },
                                    },
                                    standardSuccess: {
                                        backgroundColor: variables.sucess,
                                        color: variables.alert.text,
                                    },
                                    standardError: {
                                        backgroundColor: variables.alert.error,
                                        color: variables.alert.text,
                                    },
                                    standardWarning: {
                                        backgroundColor: variables.alert.warning,
                                    },
                                    standardInfo: {
                                        backgroundColor: variables.alert.info,
                                        color: variables.alert.text,
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
