import { Box, Chip, FilledInput, FormControl, InputLabel, MenuItem, Select, SelectChangeEvent } from "@mui/material";
import { random } from "lodash";
import React, { useMemo } from "react";
import { Truncate } from "./cellRenderers/truncate";

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
        <FormControl fullWidth variant="filled">
            <InputLabel id={labelId}>{label}</InputLabel>
            <Select<string[]>
                labelId={labelId}
                value={value}
                label={label}
                onChange={(e: SelectChangeEvent<string[]>) => onChange([].concat(e.target.value))}
                multiple
                input={<FilledInput />}
                renderValue={(selected) => (
                    <Box
                        component={Truncate}
                        renderTruncator={({ hiddenItemsCount }) => <>...</>}
                        sx={{ display: "flex", columnGap: 0.5, rowGap: 1 }}
                    >
                        {selected.map((v) => (
                            <Chip
                                key={v}
                                label={v}
                                size="small"
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
