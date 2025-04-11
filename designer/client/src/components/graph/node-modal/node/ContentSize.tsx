import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { DEFAULT_WINDOW_WIDTH } from "../../../../windowManager/useWindows";

const ModalContentDark = ({
    className,
    ...props
}: PropsWithChildren<{
    className?: string;
}>) => {
    return <div data-testid="content-size" className={cx("modalContentDark", className)} {...props} />;
};

export const ContentSize = styled(ModalContentDark)({
    padding: "1.0em 0.5em 1.5em",
    display: "flex",
    ">div": {
        // minimal content size for dynamic width windows to expand to. below that size horizontal scrollbar appears.
        // 100 is safe margin for DEFAULT_WINDOW_WIDTH sized windows for some scrollbars etc.
        width: DEFAULT_WINDOW_WIDTH - 100,
        flex: 1,
    },
});
