import { css } from "@mui/material";
import { CSSProperties } from "react";

export const nodeInputCss = (extended?: CSSProperties) =>
    css({
        height: "35px",
        width: "100%",
        padding: "0 10px",
        border: "none",
        '&[type="checkbox"]': {
            height: "20px",
        },
        ...extended,
    });
