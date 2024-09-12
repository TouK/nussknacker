import React from "react";
import { ToggleItemsButton, ToggleItemsRoot } from "./styled";
import { Divider } from "@mui/material";

interface Props {
    handleHideRow(index: number, sameItemOccurrence: number): void;
    index: number;
    sameItemOccurrence: number;
}

export const LessItemsButton = ({ handleHideRow, index, sameItemOccurrence }: Props) => {
    return (
        <ToggleItemsRoot>
            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.primary.main })} />
            <ToggleItemsButton
                onClick={() => {
                    handleHideRow(index, sameItemOccurrence);
                }}
            >
                Show less
            </ToggleItemsButton>
        </ToggleItemsRoot>
    );
};
