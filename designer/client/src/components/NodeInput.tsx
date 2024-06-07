import { css } from "@mui/material";

export const nodeInputCss = (extended?: any) =>
    css({
        height: "35px",
        width: "100%",
        padding: "0 10px",
        border: "none",
        ...extended,
        '&[type="checkbox"]': {
            height: "20px",
        },
    });
