import { useTheme } from "@mui/material";
import { WindowManagerProvider } from "@touk/window-manager";
import type { PropsWithChildren } from "react";
import React from "react";

import { blendDarken } from "../containers/theme/helpers";
import { ContentGetter } from "./ContentGetter";

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
                    borderColor: blendDarken(palette.common.black, 0.24),
                    focusColor: palette.primary.main,
                    mutedColor: palette.text.secondary,
                    primaryBackground: palette.background.paper,
                    secondaryBackground: palette.background.paper,
                },
                zIndex: zIndex.modal - 5, // elements using mui modal zIndex (e.g. menu, click outside mask) should be over our modals
            }}
            contentGetter={ContentGetter}
            {...props}
        />
    );
}
