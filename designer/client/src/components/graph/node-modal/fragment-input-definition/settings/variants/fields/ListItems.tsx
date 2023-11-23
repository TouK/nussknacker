import { Chip } from "@mui/material";
import React from "react";
import { FixedValuesOption } from "../../../item";
import { ListItemContainer, ListItemWrapper } from "./StyledSettingsComponnets";
import { Option } from "../../../TypeSelect";
import { ValidationLabel } from "../../../../../../common/ValidationLabel";
import { Error } from "../../../../editors/Validators";

interface ListItemsProps {
    items: (FixedValuesOption | Option)[];
    handleDelete?: (currentIndex: number) => void;
    errors: Error[];
}

export const ListItems = ({ items, handleDelete, errors = [] }: ListItemsProps) => {
    return (
        <ListItemContainer>
            <ListItemWrapper>
                {items.map((item, index) => {
                    const hasError = errors.some((error) => error.description.includes(`: ${item.label}`));

                    return (
                        <Chip
                            color={hasError ? "error" : undefined}
                            variant="outlined"
                            sx={{ marginRight: "10px", marginBottom: "10px" }}
                            key={index}
                            label={item.label}
                            onDelete={handleDelete && (() => handleDelete(index))}
                        />
                    );
                })}
                {errors?.map((error, index) => {
                    const item = items?.find((item) => error.description.includes(`: ${item.label}`));

                    if (!item) {
                        return null;
                    }

                    return (
                        <ValidationLabel type={"ERROR"} key={index}>
                            {item.label}: {error.message}
                        </ValidationLabel>
                    );
                })}
            </ListItemWrapper>
        </ListItemContainer>
    );
};
