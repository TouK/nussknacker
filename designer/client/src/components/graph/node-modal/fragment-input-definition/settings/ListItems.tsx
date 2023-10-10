import { Chip, styled } from "@mui/material";
import React from "react";
import { onChangeType } from "../item";

interface ListItemsProps {
    addListItem: string[];
    onChange: (path: string, value: onChangeType) => void;
    path: string;
}

const ListItemWrapper = styled("div")`
    width: 70%;
    display: flex;
    justify-content: flex-start;
    max-height: 100px;
    flex-wrap: wrap;
    overflow: auto;
    margin-top: 10px;
    ::-webkit-scrollbar-track {
        width: 15px;
        height: 100px;
        background: rgba(51, 51, 51, 1);
    }
    ::-webkit-scrollbar-thumb {
        background: rgba(173, 173, 173, 1);
        background-clip: content-box;
        border: 3.5px solid transparent;
        border-radius: 100px;
        height: 60px;
    }
    ::-webkit-scrollbar {
        width: 15px;
        height: 100px;
    }
`;

export const ListItems = ({ addListItem, onChange, path }: ListItemsProps) => {
    const handleDelete = (currentIndex: number) => {
        const filtredItemList = addListItem.filter((_, index) => index !== currentIndex);
        onChange(`${path}.addListItem`, filtredItemList);
    };

    return (
        <div style={{ width: "100%", display: "flex", justifyContent: "flex-end" }}>
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
        </div>
    );
};
