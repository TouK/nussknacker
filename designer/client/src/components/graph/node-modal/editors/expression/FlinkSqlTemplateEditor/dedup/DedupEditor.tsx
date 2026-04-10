import { Box, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { FormRow } from "../components/FormRow";
import type { DedupState, InputField } from "../types";
import { PartitionByField } from "./PartitionByField";

interface Props {
    fields: InputField[];
    config: DedupState;
    onChange: (config: DedupState) => void;
    readOnly?: boolean;
}

export function DedupEditor({ fields, config, onChange, readOnly }: Props) {
    const { t } = useTranslation();
    const selectedFields = fields.filter((f) => f.selected).map((f) => f.alias);
    const allFields = [...selectedFields, "record_time"];

    return (
        <Box display="flex" flexDirection="column">
            <FormRow label={t("flinkSql.dedup.partitionBy", "Partition by:")} alignItems="flex-start">
                <Box display="flex" flexDirection="column" gap={0.75} width="100%">
                    <PartitionByField
                        options={allFields}
                        value={config.partitionBy}
                        onChange={(partitionBy) => onChange({ ...config, partitionBy })}
                        readOnly={readOnly}
                        placeholder={t("flinkSql.dedup.partitionByPlaceholder", "Select partition key fields\u2026")}
                    />
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: "0.7rem", lineHeight: 1.4 }}>
                        {t(
                            "flinkSql.dedup.hint",
                            "The emitted record is the first received per key. In Flink append-only mode, only first-arrival deduplication is supported.",
                        )}
                    </Typography>
                </Box>
            </FormRow>
        </Box>
    );
}
