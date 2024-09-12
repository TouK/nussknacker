import React from "react";
import { ToggleItemsButton, ToggleItemsRoot } from "./styled";
import { Divider } from "@mui/material";

interface Props {
    handleHideData(index: number, sameItemOccurrence: number): void;
    index: number;
    sameItemOccurrence: number;
}

export const LessItemsButton = ({ handleHideData, index, sameItemOccurrence }: Props) => {
    return (
        <ToggleItemsRoot>
            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.primary.main })} />
            <ToggleItemsButton
                onClick={() => {
                    handleHideData(index, sameItemOccurrence);
                }}
            >
                Show less
            </ToggleItemsButton>
        </ToggleItemsRoot>
    );
};
