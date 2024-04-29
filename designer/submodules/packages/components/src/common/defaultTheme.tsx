import { cyan, deepOrange, lime } from "@mui/material/colors";
import { alpha, createTheme, Theme } from "@mui/material/styles";
import { useEffect, useMemo, useState } from "react";
import { getBorderColor, blendLighten } from "nussknackerUi/themeHelpers";

const darkBase = createTheme({
    palette: {
        mode: "dark",
        primary: {
            main: lime["500"],
        },
        secondary: {
            main: deepOrange["900"],
        },
        background: {
            default: "#333333",
        },
    },
});

const lightBase = createTheme({
    palette: {
        mode: "light",
        primary: {
            main: cyan["700"],
        },
        secondary: {
            main: deepOrange["900"],
        },
        background: {
            default: "#CCCCCC",
        },
    },
});

function useModeCheck() {
    const query = "(prefers-color-scheme: light)";
    const [isLight, setLight] = useState(window.matchMedia?.(query).matches);
    useEffect(() => {
        const listener = (event: MediaQueryListEvent) => setLight(event.matches);
        window.matchMedia?.(query).addEventListener("change", listener);
        return () => window.matchMedia?.(query).removeEventListener("change", listener);
    }, [query]);
    return isLight;
}

export const useDefaultTheme = (parent = {}): Theme => {
    const isLight = useModeCheck();

    const root = useMemo(() => createTheme(isLight ? lightBase : darkBase, parent), [isLight, parent]);
    const light = useMemo(() => root.palette.mode === "light", [root.palette.mode]);
    const bottomLineColor = useMemo(() => (light ? "rgba(0, 0, 0, 0.42)" : "rgba(255, 255, 255, 0.42)"), [light]);
    const backgroundColor = useMemo(() => (light ? "rgba(0, 0, 0, 0.06)" : "rgba(0, 0, 0, 0.25)"), [light]);
    return useMemo(
        () =>
            createTheme(root, {
                components: {
                    MuiDataGrid: {
                        styleOverrides: {
                            withBorderColor: ({ theme }) => ({
                                borderColor: getBorderColor(theme),
                            }),
                            root: {
                                border: 0,
                            },
                            row: {
                                ":nth-of-type(even):not(:hover)": {
                                    backgroundColor: alpha(root.palette.action.hover, root.palette.action.hoverOpacity * 0.2),
                                },
                            },
                            overlay: {
                                backgroundColor: alpha(root.palette.common.black, root.palette.action.hoverOpacity * 3),
                                zIndex: root.zIndex.mobileStepper - 1,
                            },
                            columnHeadersInner: {
                                backgroundColor: blendLighten(root.palette.background.paper, 0.12),
                            },
                            cell: {
                                "&:focus-within": {
                                    outlineColor: root.palette.primary.main,
                                },
                                ".Mui-focusVisible": {
                                    outline: "none", // Remove the white cell outline when the cell is focused via the keyboard, a link is clicked, and the user returns to the previous page
                                },
                            },

                            columnHeaderTitleContainer: {
                                padding: "0 6px",
                            },
                            "cell--withRenderer": {
                                "&.noPadding": {
                                    padding: 0,
                                },
                                "&.stretch": {
                                    alignItems: "stretch",
                                },
                            },
                            sortIcon: ({ theme }) => ({
                                color: theme.palette.text.secondary,
                            }),
                            menuIconButton: {
                                color: "currentColor",
                            },
                            columnSeparator: {
                                color: "currentColor",
                            },
                        },
                    },
                    MuiOutlinedInput: {
                        styleOverrides: {
                            input: {
                                outline: "none",
                            },
                        },
                    },
                    MuiFilledInput: {
                        styleOverrides: {
                            root: {
                                backgroundColor,
                                ":before": {
                                    borderBottomColor: bottomLineColor,
                                },
                            },
                        },
                    },
                    MuiChip: {
                        styleOverrides: {
                            root: {
                                borderRadius: "5px",
                                overflow: "hidden",
                                maxWidth: "50vw",
                                lineHeight: "2em",
                                "&.MuiLink-root": {
                                    cursor: "pointer",
                                },
                            },
                        },
                    },
                    MuiListItemButton: {
                        styleOverrides: {
                            divider: ({ theme }) => ({
                                borderColor: getBorderColor(theme),
                            }),
                        },
                    },
                    MuiListItemIcon: {
                        styleOverrides: {
                            root: {
                                color: "currentColor",
                            },
                        },
                    },
                },
            }),
        [backgroundColor, bottomLineColor, root],
    );
};
