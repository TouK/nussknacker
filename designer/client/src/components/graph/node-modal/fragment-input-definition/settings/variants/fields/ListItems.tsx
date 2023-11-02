import { Chip } from "@mui/material";
import React from "react";
import { FixedValuesOption, onChangeType } from "../../../item";
import { ListItemContainer, ListItemWrapper } from "./StyledSettingsComponnets";

interface ListItemsProps {
    fixedValuesList: FixedValuesOption[];
    onChange: (path: string, value: onChangeType) => void;
    path: string;
}

export const ListItems = ({ fixedValuesList, onChange, path }: ListItemsProps) => {
    const handleDelete = (currentIndex: number) => {
        const filteredItemList = fixedValuesList.filter((_, index) => index !== currentIndex);
        onChange(`${path}.fixedValuesList`, filteredItemList);
    };

    return (
        <ListItemContainer>
            <ListItemWrapper>
                {fixedValuesList.map((item, index) => (
                    <Chip
                        variant="outlined"
                        sx={{ marginRight: "10px", marginBottom: "10px" }}
                        key={index}
                        label={item.label}
                        onDelete={() => handleDelete(index)}
                    />
                ))}
            </ListItemWrapper>
        </ListItemContainer>
    );
};
