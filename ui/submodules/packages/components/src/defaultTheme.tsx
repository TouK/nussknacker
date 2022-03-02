import { cyan, deepOrange, lime } from "@mui/material/colors";
import { alpha, createTheme, Theme } from "@mui/material/styles";
import { useEffect, useState } from "react";

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

    const root = createTheme(isLight ? lightBase : darkBase, parent);
    const light = root.palette.mode === "light";
    const bottomLineColor = light ? "rgba(0, 0, 0, 0.42)" : "rgba(255, 255, 255, 0.42)";
    const backgroundColor = light ? "rgba(0, 0, 0, 0.06)" : "rgba(0, 0, 0, 0.25)";
    return createTheme(root, {
        components: {
            MuiDataGrid: {
                styleOverrides: {
                    root: {
                        border: 0,
                    },
                    row: {
                        ":nth-of-type(even):not(:hover)": {
                            backgroundColor: alpha(root.palette.action.hover, root.palette.action.hoverOpacity * 1.5),
                        },
                    },
                    columnHeadersInner: {
                        backgroundColor: root.palette.augmentColor({ color: { main: root.palette.background.paper } })[root.palette.mode],
                    },
                    "cell--withRenderer": {
                        "&.noPadding": {
                            padding: 0,
                        },
                        "&.stretch": {
                            alignItems: "stretch",
                        },
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
                    },
                },
            },
        },
    });
};
