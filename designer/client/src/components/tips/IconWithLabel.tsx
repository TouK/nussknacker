import { css } from "@emotion/css";
import { useTheme } from "@mui/material";
import type { ComponentType, SVGProps } from "react";
import React from "react";

export function IconWithLabel({
    icon: Icon,
    message,
}: {
    icon: ComponentType<SVGProps<SVGSVGElement>>;
    message: string;
}): React.JSX.Element {
    const theme = useTheme();
    return (
        <div className={css({ display: "flex", alignItems: "center" })}>
            <Icon className={css({ width: "1em", height: "1em", color: theme.palette.warning.main })} />
            <span className={css({ marginLeft: ".5em" })}>{message}</span>
        </div>
    );
}
