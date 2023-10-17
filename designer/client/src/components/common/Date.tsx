import React from "react";
import { formatAbsolutely, formatRelatively } from "../../common/DateUtils";
import { styled } from "@mui/material";

const StyledDate = styled("span")(({ theme }) => ({
    whiteSpace: "normal",
    color: theme.custom.colors.silverChalice,
}));

export default function Date({ date }: { date: string }): JSX.Element {
    return <StyledDate title={formatAbsolutely(date)}>{formatRelatively(date)}</StyledDate>;
}
