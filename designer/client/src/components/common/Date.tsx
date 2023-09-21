import React from "react";
import { formatAbsolutely, formatRelatively } from "../../common/DateUtils";
import { variables } from "../../stylesheets/variables";
import styled from "@emotion/styled";

const StyledDate = styled.span(() => ({
    whiteSpace: "normal",
    color: variables.commentHeaderColor,
}));

export default function Date({ date }: { date: string }): JSX.Element {
    return <StyledDate title={formatAbsolutely(date)}>{formatRelatively(date)}</StyledDate>;
}
