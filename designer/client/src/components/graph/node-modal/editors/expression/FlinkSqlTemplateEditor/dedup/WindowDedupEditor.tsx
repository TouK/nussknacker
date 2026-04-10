import { Box } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { DurationField } from "../components/DurationField";
import { FormRow } from "../components/FormRow";
import type { InputField, WindowDedupState } from "../types";
import { PartitionByField } from "./PartitionByField";

interface Props {
    fields: InputField[];
    config: WindowDedupState;
    onChange: (config: WindowDedupState) => void;
    readOnly?: boolean;
}

export function WindowDedupEditor({ fields, config, onChange, readOnly }: Props) {
    const { t } = useTranslation();
    const selectedFields = fields.filter((f) => f.selected).map((f) => f.alias);
    const allFields = [...selectedFields, "record_time"];

    return (
        <Box display="flex" flexDirection="column">
            <FormRow label={t("flinkSql.windowDedup.partitionBy", "Partition by:")} alignItems="flex-start">
                <PartitionByField
                    options={allFields}
                    value={config.partitionBy}
                    onChange={(partitionBy) => onChange({ ...config, partitionBy })}
                    readOnly={readOnly}
                    placeholder={t("flinkSql.windowDedup.partitionByPlaceholder", "Select partition key fields\u2026")}
                />
            </FormRow>

            <FormRow label={t("flinkSql.windowDedup.windowSize", "Window size:")} alignItems="center">
                <DurationField
                    value={config.windowSize}
                    onChange={(windowSize) => windowSize && onChange({ ...config, windowSize })}
                    disabled={readOnly}
                />
            </FormRow>
        </Box>
    );
}
