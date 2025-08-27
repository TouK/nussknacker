import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";
import { Outlet } from "react-router-dom";

const Main = styled("main")({ overflow: "auto" });

export const MainOutlet = ({ children = <Outlet /> }: PropsWithChildren) => {
    return <Main>{children}</Main>;
};
