import type { Theme } from "@mui/material/styles";
import { createTheme } from "@mui/material/styles";
import { blendLighten, getBorderColor } from "nussknackerUi/themeHelpers";
import { useMemo } from "react";

import { useDefaultTheme } from "./defaultTheme";

export const useNuTheme = (parent: Theme): Theme => {
    const root = useDefaultTheme(parent);
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
                                "--DataGrid-containerBackground": blendLighten(parent.palette.background.paper, 0.12),
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
                },
            }),
        [parent],
    );
};
