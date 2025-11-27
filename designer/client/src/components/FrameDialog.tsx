import { styled } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import React, { useMemo } from "react";

import { WindowContent } from "../windowManager/WindowContent";
import type { WindowKind } from "../windowManager/WindowKind";

const FullSizeBorderlessIFrame = styled("iframe")(({ theme }) => ({
    border: 0,
    background: theme.palette.common.white,
    width: "100%",
    height: "100%",
    minWidth: 0,
    minHeight: 0,
}));

export function FrameDialog(props: WindowContentProps<WindowKind, string>): React.JSX.Element {
    const {
        data: { meta },
    } = props;
    const components = useMemo(
        () => ({
            Content: () => <FullSizeBorderlessIFrame src={meta} />,
            Footer: () => null,
        }),
        [meta],
    );

    return <WindowContent {...props} components={components} />;
}

export default FrameDialog;
