import type { WindowContentProps } from "@touk/window-manager";
import type { ReactNode } from "react";
import React from "react";

import { WindowContent } from "../windowManager/WindowContent";
import type { WindowKind } from "../windowManager/WindowKind";

export type DialogWithChildrenProps = { children: ReactNode };

export function DialogWithChildren({ ...props }: WindowContentProps<WindowKind, DialogWithChildrenProps>): React.JSX.Element {
    return <WindowContent {...props}>{props.data.meta.children}</WindowContent>;
}
