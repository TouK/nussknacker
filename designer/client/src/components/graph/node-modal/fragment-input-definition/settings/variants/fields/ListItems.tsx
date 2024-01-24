import { Chip, FormHelperText } from "@mui/material";
import React from "react";
import { FieldName, FixedValuesOption } from "../../../item";
import { ListItemContainer, ListItemWrapper } from "./StyledSettingsComponnets";
import { Option } from "../../../TypeSelect";
import { NodeValidationError } from "../../../../../../../types";

interface ListItemsProps {
    items: (FixedValuesOption | Option)[];
    handleDelete?: (currentIndex: number) => void;
    errors: NodeValidationError[];
    fieldName: FieldName;
}

export const ListItems = ({ items, handleDelete, errors = [], fieldName }: ListItemsProps) => {
    return (
        <ListItemContainer>
            <ListItemWrapper>
                {items.map((item, index) => {
                    const hasError = errors.some((error) => error.fieldName === fieldName);

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
                {errors
                    ?.filter((error) => error.fieldName === fieldName)
                    .map((error, index) => {
                        const item = items?.find((item) => error.description.includes(`: ${item.label}`));

                        if (!item) {
                            return null;
                        }

                        return (
                            <FormHelperText title={`${item.label}: ${error.message}`} error key={index}>
                                {item.label}: {error.message}
                            </FormHelperText>
                        );
                    })}
            </ListItemWrapper>
        </ListItemContainer>
    );
};
