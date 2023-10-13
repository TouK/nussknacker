import { Chip } from "@mui/material";
import React from "react";
import { onChangeType } from "../item";
import { ListItemContainer, ListItemWrapper } from "./StyledSettingsComponnets";

interface ListItemsProps {
    fixedValueList: string[];
    onChange: (path: string, value: onChangeType) => void;
    path: string;
}

export const ListItems = ({ fixedValueList, onChange, path }: ListItemsProps) => {
    const handleDelete = (currentIndex: number) => {
        const filtredItemList = fixedValueList.filter((_, index) => index !== currentIndex);
        onChange(`${path}.fixedValueList`, filtredItemList);
    };

    return (
        <ListItemContainer>
            <ListItemWrapper>
                {fixedValueList.map((item, index) => (
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
