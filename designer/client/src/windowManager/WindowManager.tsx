import React, { PropsWithChildren } from "react";
import { useTheme } from "@mui/material";
import { WindowManagerProvider } from "@touk/window-manager";
import { ContentGetter } from "./ContentGetter";

import { blendDarken } from "../containers/theme/helpers";

export function WindowManager(props: PropsWithChildren<{ className: string }>) {
    const {
        zIndex,
        palette,
        custom: { spacing },
    } = useTheme();

    return (
        <WindowManagerProvider
            theme={{
                backgroundOpacity: 1,
                backdropFilter: "none",
                spacing: {
                    baseUnit: spacing.baseUnit,
                },
                colors: {
                    borderColor: blendDarken(palette.background.paper, 0.24),
                    focusColor: palette.primary.main,
                    mutedColor: palette.text.secondary,
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
