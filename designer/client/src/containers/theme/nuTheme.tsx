import type { Palette, PaletteMode } from "@mui/material";
import { alpha, createTheme } from "@mui/material";
import type { ThemeOptions } from "@mui/material/styles/createTheme";
import { deepmerge } from "@mui/utils";
import type { Dispatch, SetStateAction } from "react";

import type { NodeType } from "../../types/node";
import { WindowKind } from "../../windowManager/WindowKind";
import type { EnvironmentTagColor } from "../EnvironmentTag";
import { darkModePalette } from "./darkModePalette";
import { blendDarken, blendLighten } from "./helpers";
import { lightModePalette } from "./lightModePalette";
import { fontFamily, formLabelWidth, globalStyles } from "./styles";

declare module "@mui/material/FormHelperText" {
    interface FormHelperTextPropsVariantOverrides {
        largeMessage: true;
    }

    interface FormHelperTextOwnProps {
        "data-testid"?: string;
    }
}

interface NodePalette {
    fill: string;
}

interface WindowPalette {
    backgroundColor: string;
    color: string;
}

interface CodeEditorPalette {
    color: string;
}

export interface CustomPalette {
    nodes: {
        [type: string]: NodePalette;
    };
    environmentAlert: {
        [Tag in EnvironmentTagColor]: string;
    };
    windows: {
        [type: string]: WindowPalette;
        default: WindowPalette;
    };
    codeEditor: {
        string: CodeEditorPalette;
        numeric: CodeEditorPalette;
        objectKeys: CodeEditorPalette;
        readonly: {
            backgroundColor: string;
        };
    };
}

declare module "@mui/material/styles" {
    interface Palette {
        custom: ReturnType<typeof extendWithHelpers>;
    }

    interface PaletteOptions {
        custom?: CustomPalette;
    }

    type SetMode = Dispatch<SetStateAction<PaletteMode>>;

    interface Theme {
        custom: typeof custom;
        setMode: SetMode;
    }

    interface ThemeOptions {
        custom?: typeof custom;
        setMode?: SetMode;
    }
}

const custom = {
    ConnectionErrorModal: {
        zIndex: 1600,
    },
    spacing: {
        controlHeight: 36,
        baseUnit: 4,
    },
    fontSize: 14,
};

const extendWithHelpers = (custom: CustomPalette) => ({
    ...custom,
    getNodeStyles: function (this: CustomPalette, nodeType: NodeType["type"]) {
        return this.nodes[nodeType] || { fill: "inherit" };
    },
    getWindowStyles: function (this: CustomPalette, type = WindowKind.default) {
        switch (type) {
            case WindowKind.compareVersions:
            case WindowKind.calculateCounts:
                return this.windows.compareVersions;
            case WindowKind.editProperties:
                return this.windows.editProperties;
            default:
                return this.windows.default;
        }
    },
});

export function getDesignTokens(mode: PaletteMode): ThemeOptions {
    const modePalette = mode === "light" ? lightModePalette : darkModePalette;

    return {
        palette: {
            mode,
            ...modePalette,
            custom: extendWithHelpers(modePalette.custom),
        },
    };
}

const headerCommonStyles = {
    fontWeight: 500,
    lineHeight: 1.1,
    marginTop: "20px",
    marginBottom: "10px",
};

export const nuTheme = (mode: PaletteMode, setMode: Dispatch<SetStateAction<PaletteMode>>) => {
    const themeOptions: ThemeOptions = {
        typography: (palette: Palette) => ({
            fontFamily,
            h1: { ...headerCommonStyles },
            h2: { ...headerCommonStyles },
            h3: { ...headerCommonStyles },
            h4: { ...headerCommonStyles },
            h5: { ...headerCommonStyles },
            h6: { ...headerCommonStyles },
            subtitle1: {
                fontWeight: "bold",
            },
            subtitle2: {
                fontWeight: "bold",
            },
            overline: {
                fontSize: ".6875rem",
                letterSpacing: "inherit",
                lineHeight: "inherit",
                textTransform: "inherit",
                color: palette.text.secondary,
            },
        }),
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
                    root: ({ theme }) => ({
                        width: 300,
                        zIndex: 20000,
                        marginTop: 10,
                        cursor: "pointer",
                        maxHeight: 400,
                        ".MuiAlert-icon": {
                            color: alpha(theme.palette.common.black, 0.54),
                            alignSelf: "center",
                        },
                    }),
                    standardSuccess: ({ theme }) => ({
                        backgroundColor: theme.palette.success.main,
                        color: blendDarken(theme.palette.text.primary, 0.9),
                    }),
                    standardError: ({ theme }) => ({
                        backgroundColor: blendLighten(theme.palette.error.main, 0.15),
                        color: blendDarken(theme.palette.text.primary, 0.9),
                    }),
                    standardWarning: ({ theme }) => ({
                        backgroundColor: theme.palette.warning.main,
                        color: blendDarken(theme.palette.text.primary, 0.9),
                    }),
                    standardInfo: ({ theme }) => ({
                        backgroundColor: theme.palette.primary.main,
                        color: blendDarken(theme.palette.text.primary, 0.9),
                    }),
                },
            },
            MuiCssBaseline: {
                styleOverrides: (theme) => globalStyles(theme),
            },
            MuiFormControl: {
                defaultProps: {
                    variant: "standard",
                },
                styleOverrides: {
                    root: ({ ownerState }) => {
                        if (ownerState.variant === "standard") {
                            return {
                                display: "flex",
                                flexDirection: "row",
                                margin: "16px 0",
                                ".MuiFormLabel-root": {
                                    display: "flex",
                                    flexBasis: formLabelWidth,
                                    maxWidth: "20em",
                                    overflowWrap: "anywhere",
                                    marginTop: "9px",
                                },
                                ".MuiFormLabel-root:has(+ .MuiBox-root [role='tablist'])": {
                                    marginTop: "27px",
                                },
                                ".MuiFormLabel-root:has([aria-label='Data type']):has(+ .MuiBox-root [role='tablist'])": {
                                    marginTop: "18px",
                                },
                                ".MuiFormLabel-root:has([aria-label='Data type'])": {
                                    marginTop: 0,
                                },
                            };
                        }
                    },
                },
            },
            MuiFormLabel: {
                defaultProps: {
                    focused: false,
                },
                styleOverrides: {
                    root: ({ theme }) => ({
                        ...theme.typography.body2,
                    }),
                },
            },
            MuiFormHelperText: {
                styleOverrides: {
                    root: ({ theme }) => ({
                        marginLeft: 0,
                        color: theme.palette.success.main,
                        "&.Mui-error": {
                            color: theme.palette.error.light,
                        },
                    }),
                },
                variants: [
                    {
                        props: { variant: "largeMessage" },
                        style: { fontSize: ".875rem" },
                    },
                ],
                defaultProps: {
                    "data-testid": "form-helper-text",
                },
            },
            MuiAutocomplete: {
                styleOverrides: {
                    noOptions: ({ theme }) => ({
                        ...theme.typography.body2,
                        padding: theme.spacing(0.75, 2),
                        marginTop: theme.spacing(0.5),
                        backgroundColor: theme.palette.background.paper,
                    }),
                    loading: ({ theme }) => ({
                        ...theme.typography.body2,
                        padding: theme.spacing(0.75, 2),
                        marginTop: theme.spacing(0.5),
                        backgroundColor: theme.palette.background.paper,
                    }),
                },
            },
            MuiSelect: {
                styleOverrides: {
                    root: ({ theme }) => ({
                        borderRadius: 0,
                        margin: "-1px", // FIXME: other fields have outline instead of border, so we need to compensate for that
                        "&.Mui-error .MuiOutlinedInput-notchedOutline": {
                            borderColor: theme.palette.error.light,
                        },
                        "&, &.Mui-focused": {
                            ".MuiOutlinedInput-notchedOutline": {
                                borderWidth: 1,
                            },
                        },
                    }),
                    select: ({ theme }) => ({
                        padding: 0,
                        paddingRight: theme.spacing(3),
                    }),
                },
            },
            MuiChip: {
                styleOverrides: {
                    colorError: ({ theme }) => ({
                        borderColor: theme.palette.error.light,
                        color: "inherit",
                        ".MuiChip-deleteIcon": {
                            color: theme.palette.error.light,
                        },
                    }),
                },
            },
        },
        custom,
        setMode,
    };
    return createTheme(deepmerge(getDesignTokens(mode), themeOptions));
};
