import React, { PropsWithChildren } from "react";
import { useTheme } from "@emotion/react";
import { WindowManagerProvider } from "@touk/window-manager";
import { contentGetter } from "./ContentGetter";

export function WindowManager(props: PropsWithChildren<{ className: string }>) {
    const theme = useTheme();
    return <WindowManagerProvider theme={theme} contentGetter={contentGetter} {...props} />;
}
