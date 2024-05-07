import { css, cx } from "@emotion/css";
import React, { PropsWithChildren } from "react";

export function ContentSize({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <div
            data-testid="content-size"
            className={cx("modalContentDark", css({ padding: "1.0em 0.5em 1.5em", display: "flex", ">div": { width: 750, flex: 1 } }))}
        >
            {children}
        </div>
    );
}
