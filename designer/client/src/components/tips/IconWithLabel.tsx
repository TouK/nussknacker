import { css } from "@emotion/css";
import React, { ComponentType, SVGProps } from "react";
import { useTheme } from "@mui/material";

export function IconWithLabel({ icon: Icon, message }: { icon: ComponentType<SVGProps<SVGSVGElement>>; message: string }): JSX.Element {
    const theme = useTheme();
    return (
        <div className={css({ display: "flex", alignItems: "center" })}>
            <Icon className={css({ width: "1em", height: "1em", color: theme.palette.warning.main })} />
            <span className={css({ marginLeft: ".5em" })}>{message}</span>
        </div>
    );
}
