import { Box, FormControl, MenuItem, Select, TextField, ToggleButton, ToggleButtonGroup, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { getBorderColor } from "../../../../../../../containers/theme/helpers";
import { DurationField } from "../components/DurationField";
import { FormRow } from "../components/FormRow";
import { nuMenuProps, nuSelectSx } from "../components/nuInputSx";
import { PartitionByField } from "../dedup/PartitionByField";
import type { InputField, WindowTopNState } from "../types";

interface Props {
    fields: InputField[];
    config: WindowTopNState;
    onChange: (config: WindowTopNState) => void;
    readOnly?: boolean;
}

export function WindowTopNEditor({ fields, config, onChange, readOnly }: Props) {
    const { t } = useTranslation();
    const selectedFields = fields.filter((f) => f.selected).map((f) => f.alias);
    const allFields = [...selectedFields, "record_time"];

    return (
        <Box display="flex" flexDirection="column">
            {/* Partition By */}
            <FormRow label={t("flinkSql.windowTopN.partitionBy", "Partition by:")} alignItems="flex-start">
                <PartitionByField
                    options={allFields}
                    value={config.partitionBy}
                    onChange={(partitionBy) => onChange({ ...config, partitionBy })}
                    readOnly={readOnly}
                    placeholder={t("flinkSql.windowTopN.partitionByPlaceholder", "Select partition fields\u2026")}
                />
            </FormRow>

            {/* Order By */}
            <FormRow label={t("flinkSql.windowTopN.orderBy", "Order by:")} alignItems="center">
                <Box display="flex" gap={1} width="100%">
                    <FormControl size="small" variant="outlined" sx={{ flex: 1 }}>
                        <Select
                            value={config.orderBy}
                            onChange={(e) => onChange({ ...config, orderBy: e.target.value })}
                            disabled={readOnly || allFields.length === 0}
                            displayEmpty
                            renderValue={(v) =>
                                v || (
                                    <Typography variant="body2" color="text.disabled" sx={{ fontSize: "0.8rem" }}>
                                        {t("flinkSql.windowTopN.orderByPlaceholder", "Select ranking field\u2026")}
                                    </Typography>
                                )
                            }
                            MenuProps={nuMenuProps}
                            sx={nuSelectSx}
                        >
                            {allFields.map((f) => (
                                <MenuItem key={f} value={f}>
                                    {f}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <ToggleButtonGroup
                        value={config.orderDir}
                        exclusive
                        size="small"
                        onChange={(_, val) => val && onChange({ ...config, orderDir: val })}
                        disabled={readOnly}
                        sx={{
                            height: 35,
                            "& .MuiToggleButton-root": {
                                textTransform: "none",
                                px: 1.5,
                                fontSize: "0.75rem",
                                fontWeight: 600,
                                borderRadius: 0,
                            },
                        }}
                    >
                        <ToggleButton value="ASC">ASC</ToggleButton>
                        <ToggleButton value="DESC">DESC</ToggleButton>
                    </ToggleButtonGroup>
                </Box>
            </FormRow>

            {/* Top N */}
            <FormRow label={t("flinkSql.windowTopN.n", "Top N:")} alignItems="center">
                <TextField
                    type="number"
                    size="small"
                    value={config.n}
                    onChange={(e) => {
                        const v = parseInt(e.target.value, 10);
                        if (!isNaN(v) && v > 0) onChange({ ...config, n: v });
                    }}
                    disabled={readOnly}
                    inputProps={{ min: 1 }}
                    sx={(theme) => ({
                        width: 100,
                        "& .MuiOutlinedInput-root": {
                            borderRadius: 0,
                            backgroundColor: "background.paper",
                            fontSize: "0.85rem",
                            height: 35,
                            outline: `1px solid ${getBorderColor(theme)}`,
                            "&:focus-within": { outline: `1px solid ${theme.palette.primary.main}` },
                            "& fieldset": { border: "none" },
                            "&:hover fieldset": { border: "none" },
                            "&.Mui-focused fieldset": { border: "none" },
                        },
                    })}
                />
            </FormRow>

            {/* Window Size */}
            <FormRow label={t("flinkSql.windowTopN.windowSize", "Window size:")} alignItems="center">
                <DurationField
                    value={config.windowSize}
                    onChange={(windowSize) => windowSize && onChange({ ...config, windowSize })}
                    disabled={readOnly}
                />
            </FormRow>
        </Box>
    );
}
