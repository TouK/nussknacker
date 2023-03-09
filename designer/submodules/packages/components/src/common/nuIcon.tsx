import React from "react";
import loadable from "@loadable/component";
import { styled } from "@mui/material";

const Icon = loadable(async () => import("nussknackerUi/Icon"));
export const NuIcon = styled(Icon)((props) => {
    const { primary, warning, error, success } = props.theme.palette;
    return {
        width: "1em",
        height: "1em",
        color: primary.main,
        "--warnColor": warning.main,
        "--errorColor": error.main,
        "--successColor": success.main,
    };
});
