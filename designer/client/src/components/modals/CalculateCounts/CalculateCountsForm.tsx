import { Box } from "@mui/material";
import moment from "moment";
import React, { useCallback, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";

import type { CountsState } from "./CalculateCountsDialog";
import { CountsRanges } from "./CountsRanges";
import type { PickerInput } from "./Picker";
import { Picker } from "./Picker";
import { RefreshSelectButton } from "./RefreshSelectButton";

export function CalculateCountsForm({
    value,
    onChange,
}: {
    value: CountsState;
    onChange: React.Dispatch<React.SetStateAction<CountsState>>;
}): React.JSX.Element {
    const { t } = useTranslation();
    const { from, to, refresh } = value;

    const setFrom = useCallback(
        (from: PickerInput) =>
            onChange((value) => ({
                ...value,
                from,
            })),
        [onChange],
    );
    const setTo = useCallback(
        (to: PickerInput) =>
            onChange((value) => ({
                ...value,
                to,
            })),
        [onChange],
    );
    const setRange = useCallback(
        ([from, to]: [PickerInput, PickerInput], refresh?: number | null) => onChange({ ...value, from, to, refresh }),
        [onChange, value],
    );
    const setRefresh = useCallback(
        (refreshSettings: number | null) =>
            onChange((value) => ({
                ...value,
                refresh: refreshSettings,
            })),
        [onChange],
    );

    const refreshAllowed = useMemo(() => moment(to).isAfter(Date.now()), [to]);
    useEffect(() => {
        if (!refreshAllowed) {
            onChange((value) => ({
                ...value,
                refresh: null,
            }));
        }
    }, [onChange, refreshAllowed]);

    return (
        <>
            <Picker label={t("calculateCounts.processCountsFrom", "Scenario counts from")} onChange={setFrom} value={from} />
            <Picker label={t("calculateCounts.processCountsTo", "Scenario counts to")} onChange={setTo} value={to} />
            <Box sx={{ display: "flex", justifyContent: "flex-end", marginY: 1 }}>
                <RefreshSelectButton onChange={setRefresh} value={refresh} disabled={!refreshAllowed} options={[10, 30, 60, 300]} />
            </Box>
            <CountsRanges label={t("calculateCounts.quickRanges", "Quick ranges")} onChange={setRange} />
        </>
    );
}
