import type { Moment } from "moment";
import moment from "moment";
import type { PropsWithChildren } from "react";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { PredefinedDropdownButton, PredefinedRangeButton } from "./CountsStyled";

export interface Range {
    name: string;
    from: () => Moment;
    to: () => Moment;
    refresh?: number;
}

interface RangesButtonsProps {
    ranges: Range[];
    onChange: (value: [Moment, Moment], refresh?: number | null) => void;
    limit?: number;
}

export function CountsRangesButtons({ children, ranges, onChange, limit = -1 }: PropsWithChildren<RangesButtonsProps>): React.JSX.Element {
    const { t } = useTranslation();
    const changeHandler = useCallback(
        (range: Range) => {
            const from = range.from();
            const to = range.to();
            const refresh = range.refresh ?? (moment().isBefore(to) ? 10 : null);
            onChange([from, to], refresh);
        },
        [onChange],
    );

    const visible = useMemo(() => (limit >= 0 ? ranges.slice(0, limit) : ranges), [ranges, limit]);
    const collapsed = useMemo(() => (limit >= 0 ? ranges.slice(limit) : []), [ranges, limit]);
    return (
        <>
            {visible.map((range) => (
                <PredefinedRangeButton
                    key={range.name}
                    type="button"
                    title={range.name}
                    onClick={() => changeHandler(range)}
                    style={{ flex: 1 }}
                >
                    {range.name}
                </PredefinedRangeButton>
            ))}

            {collapsed.length > 0 ? (
                <PredefinedDropdownButton
                    options={collapsed.map((value) => ({ label: value.name, value }))}
                    onRangeSelect={changeHandler}
                    style={{ flex: 1 }}
                    wrapperStyle={{
                        display: "flex",
                        flex: 2,
                    }}
                >
                    {children || t("calculateCounts.more", "Select more...")}
                </PredefinedDropdownButton>
            ) : null}
        </>
    );
}
