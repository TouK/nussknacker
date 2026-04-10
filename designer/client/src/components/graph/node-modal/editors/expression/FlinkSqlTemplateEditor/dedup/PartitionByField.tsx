import { Autocomplete, Box, Checkbox, Chip, TextField } from "@mui/material";
import React from "react";

import { getBorderColor } from "../../../../../../../containers/theme/helpers";

interface Props {
    options: string[];
    value: string[];
    onChange: (value: string[]) => void;
    readOnly?: boolean;
    placeholder?: string;
}

export function PartitionByField({ options, value, onChange, readOnly, placeholder }: Props) {
    return (
        <Box width="100%">
            <Autocomplete
                multiple
                disableCloseOnSelect
                options={options}
                value={value}
                disabled={readOnly || options.length === 0}
                onChange={(_, next) => onChange(next)}
                renderTags={(vals, getTagProps) =>
                    vals.map((v, i) => {
                        const { key, ...tagProps } = getTagProps({ index: i });
                        return (
                            <Chip
                                key={key}
                                {...tagProps}
                                label={v}
                                size="small"
                                sx={{ fontSize: "0.75rem", height: 20, borderRadius: 0.5 }}
                            />
                        );
                    })
                }
                renderOption={(props, option, { selected }) => {
                    const { key, ...rest } = props;
                    return (
                        <li key={key} {...rest} style={{ fontSize: "0.85rem", padding: "2px 8px" }}>
                            <Checkbox size="small" checked={selected} disableRipple sx={{ p: 0.5, mr: 0.5 }} />
                            {option}
                        </li>
                    );
                }}
                renderInput={(params) => (
                    <TextField
                        {...params}
                        placeholder={value.length === 0 ? placeholder : undefined}
                        size="small"
                        sx={(theme) => ({
                            "& .MuiOutlinedInput-root": {
                                borderRadius: 0,
                                fontSize: "0.85rem",
                                backgroundColor: "background.paper",
                                outline: `1px solid ${getBorderColor(theme)}`,
                                "&.Mui-focused": { outline: `1px solid ${theme.palette.primary.main}` },
                                "& fieldset": { border: "none" },
                                padding: "4px 40px 4px 6px !important",
                            },
                        })}
                    />
                )}
                slotProps={{
                    paper: {
                        elevation: 0,
                        sx: (theme) => ({
                            borderRadius: 0,
                            outline: `1px solid ${getBorderColor(theme)}`,
                            backgroundColor: "background.paper",
                            backgroundImage: "none",
                            "& .MuiAutocomplete-listbox": { fontSize: "0.85rem", p: 0.5 },
                        }),
                    },
                }}
            />
        </Box>
    );
}
