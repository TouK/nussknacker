import React, { PropsWithChildren } from "react";
import { useTheme } from "@mui/material";
import { WindowManagerProvider } from "@touk/window-manager";
import { ContentGetter } from "./ContentGetter";

export function WindowManager(props: PropsWithChildren<{ className: string }>) {
    const {
        zIndex,
        palette,
        custom: { spacing, colors },
    } = useTheme();

    return (
        <WindowManagerProvider
            theme={{
                spacing: {
                    baseUnit: spacing.baseUnit,
                },
                colors: {
                    borderColor: colors.borderColor,
                    focusColor: colors.focusColor,
                    mutedColor: colors.mutedColor,
                    primaryBackground: palette.background.paper,
                    secondaryBackground: palette.background.paper,
                },
                zIndex: zIndex.modal,
            }}
            contentGetter={ContentGetter}
            {...props}
        />
    );
}
