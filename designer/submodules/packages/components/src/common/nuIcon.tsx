import React from "react";
import loadable from "@loadable/component";
import { styled } from "@mui/material";

const Icon = loadable(async () => import("nussknackerUi/Icon"));
export const NuIcon = styled(Icon)(({ theme }) => ({
    width: "1em",
    height: "1em",
    color: "inherit",
}));
