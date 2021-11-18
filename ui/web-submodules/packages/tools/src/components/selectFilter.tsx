import { Box, Chip, FormControl, InputLabel, MenuItem, OutlinedInput, Select, SelectChangeEvent } from "@mui/material";
import { random } from "lodash";
import React, { useMemo } from "react";

interface SelectFilterProps {
    label: string;
    options: string[];
    value: string[];
    onChange: (value: string[]) => void;
}

export function SelectFilter(props: SelectFilterProps): JSX.Element {
    const { value = [], label, options, onChange } = props;

    const visibleOptions = useMemo(() => options || [], [options]);
    const labelId = useMemo(() => `label-${random(100000)}`, []);
    return (
        <FormControl fullWidth>
            <InputLabel id={labelId}>{label}</InputLabel>
            <Select<string[]>
                labelId={labelId}
                value={value}
                label={label}
                onChange={(e: SelectChangeEvent<string[]>) => onChange([].concat(e.target.value))}
                multiple
                input={<OutlinedInput label={label} />}
                renderValue={(selected) => (
                    <Box sx={{ display: "flex", flexWrap: "wrap", columnGap: 0.5, rowGap: 1 }}>
                        {selected.map((v) => (
                            <Chip
                                key={v}
                                label={v}
                                onPointerDown={(event) => {
                                    //select is taking over all events
                                    event.preventDefault();
                                }}
                                onDelete={() => {
                                    onChange(value.filter((c) => c !== v));
                                }}
                                onDoubleClick={() => {
                                    onChange([v]);
                                }}
                            />
                        ))}
                    </Box>
                )}
            >
                {visibleOptions.map((name) => (
                    <MenuItem key={name} value={name}>
                        {name}
                    </MenuItem>
                ))}
            </Select>
        </FormControl>
    );
}
