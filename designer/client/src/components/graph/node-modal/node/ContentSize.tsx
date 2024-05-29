import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import React, { PropsWithChildren } from "react";

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
        width: 750,
        flex: 1,
    },
});
