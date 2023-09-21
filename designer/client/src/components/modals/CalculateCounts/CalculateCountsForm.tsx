import React, { useCallback } from "react";
import { ChangeableValue } from "../../ChangeableValue";
import { CountsRanges } from "./CountsRanges";
import { Picker, PickerInput } from "./Picker";
import { useTranslation } from "react-i18next";
import { State } from ".";

export function CalculateCountsForm({ value, onChange }: ChangeableValue<State>): JSX.Element {
    const { t } = useTranslation();
    const { from, to } = value;

    const setFrom = useCallback((from: PickerInput) => onChange({ ...value, from }), [onChange, value]);
    const setTo = useCallback((to: PickerInput) => onChange({ ...value, to }), [onChange, value]);
    const setRange = useCallback(([from, to]: [PickerInput, PickerInput]) => onChange({ ...value, from, to }), [onChange, value]);

    return (
        <>
            <Picker label={t("calculateCounts.processCountsFrom", "Scenario counts from")} onChange={setFrom} value={from} />
            <Picker label={t("calculateCounts.processCountsTo", "Scenario counts to")} onChange={setTo} value={to} />
            <CountsRanges label={t("calculateCounts.quickRanges", "Quick ranges")} onChange={setRange} />
        </>
    );
}
