import { Moment } from "moment";
import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { PredefinedDropdownButton, PredefinedRangeButton } from "./CountsStyled";
import { useTranslation } from "react-i18next";

export interface Range {
    name: string;
    from: () => Moment;
    to: () => Moment;
}

interface RangesButtonsProps {
    ranges: Range[];
    onChange: (value: [Moment, Moment]) => void;
    limit?: number;
}

export function CountsRangesButtons({ children, ranges, onChange, limit = -1 }: PropsWithChildren<RangesButtonsProps>): JSX.Element {
    const { t } = useTranslation();
    const changeHandler = useCallback(({ from, to }: Range) => onChange([from(), to()]), [onChange]);

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
