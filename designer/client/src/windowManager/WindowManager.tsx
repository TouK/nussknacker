import React, { PropsWithChildren } from "react";
import { useTheme } from "@mui/material";
import { WindowManagerProvider } from "@touk/window-manager";
import { contentGetter } from "./ContentGetter";

export function WindowManager(props: PropsWithChildren<{ className: string }>) {
    const {
        zIndex,
        spacing,
        custom: { colors },
    } = useTheme();

    return (
        <WindowManagerProvider
            theme={{
                //TODO: Passing spacing breaks whole MUI functionality
                // eslint-disable-next-line @typescript-eslint/ban-ts-comment
                // @ts-ignore
                spacing,
                colors: {
                    borderColor: colors.borderColor,
                    focusColor: colors.focusColor,
                    mutedColor: colors.mutedColor,
                    primaryBackground: colors.primaryBackground,
                    secondaryBackground: colors.secondaryBackground,
                },
                zIndex: zIndex.modal,
            }}
            contentGetter={contentGetter}
            {...props}
        />
    );
}
