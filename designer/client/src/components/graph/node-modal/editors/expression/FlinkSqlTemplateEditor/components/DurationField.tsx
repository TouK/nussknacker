import { Box, FormControl, MenuItem, Select, TextField, Tooltip, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { nuMenuProps, nuSelectSx } from "./nuInputSx";

const TIME_UNITS = ["SECOND", "MINUTE", "HOUR", "DAY"] as const;
type TimeUnit = (typeof TIME_UNITS)[number];

interface Props {
    /** Current value, e.g. "5 MINUTE". Undefined means "no constraint" (only valid when optional=true). */
    value: string | undefined;
    onChange: (value: string | undefined) => void;
    disabled?: boolean;
    /** When true, renders an "optional" hint and allows clearing the field. */
    optional?: boolean;
}

function parse(value: string | undefined): { amount: string; unit: TimeUnit } {
    if (!value) return { amount: "", unit: "MINUTE" };
    const [amount, unit] = value.split(" ");
    return {
        amount: amount ?? "",
        unit: (TIME_UNITS as readonly string[]).includes(unit ?? "") ? (unit as TimeUnit) : "MINUTE",
    };
}

export function DurationField({ value, onChange, disabled, optional }: Props) {
    const { t } = useTranslation();
    const { amount, unit } = parse(value);

    const update = (newAmount: string, newUnit: TimeUnit) => {
        if (!newAmount) {
            onChange(optional ? undefined : value);
        } else {
            onChange(`${newAmount} ${newUnit}`);
        }
    };

    return (
        <Box display="flex" gap={1} alignItems="center">
            <TextField
                size="small"
                type="number"
                value={amount}
                onChange={(e) => update(e.target.value, unit)}
                disabled={disabled}
                inputProps={{ min: 1 }}
                placeholder="—"
                sx={{ width: 80, "& input": { fontSize: "0.85rem" } }}
            />
            <FormControl size="small" variant="outlined" sx={{ width: 120 }}>
                <Select
                    value={unit}
                    onChange={(e) => update(amount, e.target.value as TimeUnit)}
                    disabled={disabled}
                    MenuProps={nuMenuProps}
                    sx={nuSelectSx}
                >
                    {TIME_UNITS.map((u) => (
                        <MenuItem key={u} value={u}>
                            {u}
                        </MenuItem>
                    ))}
                </Select>
            </FormControl>
            {optional && (
                <Tooltip
                    title={t(
                        "flinkSql.duration.optionalHint",
                        "Max time between first and last event in a match. Recommended for efficient state management.",
                    )}
                    placement="top"
                >
                    <Typography variant="caption" sx={{ fontSize: "0.68rem", color: "text.disabled", cursor: "default" }}>
                        {t("flinkSql.duration.optional", "optional")}
                    </Typography>
                </Tooltip>
            )}
        </Box>
    );
}
