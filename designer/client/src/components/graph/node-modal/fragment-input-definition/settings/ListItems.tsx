import { Chip } from "@mui/material";
import React from "react";
import { onChangeType } from "../item";
import { ListItemContainer, ListItemWrapper } from "./StyledSettingsComponnets";

interface ListItemsProps {
    addListItem: string[];
    onChange: (path: string, value: onChangeType) => void;
    path: string;
}

export const ListItems = ({ addListItem, onChange, path }: ListItemsProps) => {
    const handleDelete = (currentIndex: number) => {
        const filtredItemList = addListItem.filter((_, index) => index !== currentIndex);
        onChange(`${path}.addListItem`, filtredItemList);
    };

    return (
        <ListItemContainer>
            <ListItemWrapper>
                {addListItem.map((item, index) => (
                    <Chip
                        variant="outlined"
                        sx={{ marginRight: "10px", marginBottom: "10px" }}
                        key={index}
                        label={item}
                        onDelete={() => handleDelete(index)}
                    />
                ))}
            </ListItemWrapper>
        </ListItemContainer>
    );
};
