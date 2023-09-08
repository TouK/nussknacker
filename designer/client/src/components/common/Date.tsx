import React from "react";
import { formatAbsolutely, formatRelatively } from "../../common/DateUtils";
import styled from "@emotion/styled";

const StyledDate = styled.span(() => ({
    whiteSpace: "normal",
}));

export default function Date({ date }: { date: string }): JSX.Element {
    return <StyledDate title={formatAbsolutely(date)}>{formatRelatively(date)}</StyledDate>;
}
