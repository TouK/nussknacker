import { FormHelperText } from "@mui/material";
import React from "react";

import type { NodeValidationError } from "../../../../../../../types/validation";
import { SpelChip } from "../../../../aggregate/groupBy/spelChip";
import { ValuesList } from "../../../../aggregate/groupBy/valuesList";
import type { FieldName, FixedValuesOption } from "../../../item/types";
import type { Option } from "../../../TypeSelect";
import { ListItemContainer, ListItemWrapper } from "./StyledSettingsComponnets";

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
                <ValuesList
                    values={items.map(({ label }) => label)}
                    onRemove={handleDelete}
                    isValid={() => {
                        // FIXME: moved as is but this doesn't look right
                        return !errors.some((error) => error.fieldName === fieldName);
                    }}
                    ChipComponent={SpelChip}
                    sx={{ marginLeft: 0, marginTop: 0 }}
                />

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
