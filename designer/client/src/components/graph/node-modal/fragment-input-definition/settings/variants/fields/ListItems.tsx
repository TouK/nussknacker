import { Chip } from "@mui/material";
import React from "react";
import { FixedValuesOption } from "../../../item";
import { ListItemContainer, ListItemWrapper } from "./StyledSettingsComponnets";
import { Option } from "../../../TypeSelect";

interface ListItemsProps {
    items: FixedValuesOption[] | Option[];
    handleDelete?: (currentIndex: number) => void;
}

export const ListItems = ({ items, handleDelete }: ListItemsProps) => {
    return (
        <ListItemContainer>
            <ListItemWrapper>
                {items.map((item, index) => (
                    <Chip
                        variant="outlined"
                        sx={{ marginRight: "10px", marginBottom: "10px" }}
                        key={index}
                        label={item.label}
                        onDelete={handleDelete}
                    />
                ))}
            </ListItemWrapper>
        </ListItemContainer>
    );
};
