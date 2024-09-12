import React from "react";
import { Divider } from "@mui/material";
import { ToggleItemsButton, ToggleItemsRoot } from "./styled";

interface Props {
    sameItemOccurrence: number;
    handleShowRow(index: number, sameItemOccurrence: number): void;
    index: number;
}

export const MoreItemsButton = ({ sameItemOccurrence, handleShowRow, index }: Props) => {
    return (
        <ToggleItemsRoot>
            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.primary.main })} />
            <ToggleItemsButton
                onClick={() => {
                    handleShowRow(index, sameItemOccurrence);
                }}
            >
                Show {sameItemOccurrence} more
            </ToggleItemsButton>
        </ToggleItemsRoot>
    );
};
