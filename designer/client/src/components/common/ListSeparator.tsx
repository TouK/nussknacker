import React from "react";
import styled from "@emotion/styled";
import { variables } from "../../stylesheets/variables";

const StyledSeparator = styled.hr(({ dark }: { dark?: boolean }) => ({
    border: "none",
    borderBottom: "1px solid",
    margin: "10px 0",
    color: dark ? variables.panelHeaderBackground : variables.panelTitleTextColor,
}));

export function ListSeparator(props: { dark?: boolean }): JSX.Element {
    return <StyledSeparator dark={props.dark} />;
}
