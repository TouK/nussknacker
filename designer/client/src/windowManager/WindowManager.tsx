import React, { PropsWithChildren } from "react";
import { useTheme } from "@mui/material";
import { WindowManagerProvider } from "@touk/window-manager";
import { ContentGetter } from "./ContentGetter";

export function WindowManager(props: PropsWithChildren<{ className: string }>) {
    const {
        zIndex,
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
                    primaryBackground: colors.primaryBackground,
                    secondaryBackground: colors.secondaryBackground,
                },
                zIndex: zIndex.modal,
            }}
            contentGetter={ContentGetter}
            {...props}
        />
    );
}
