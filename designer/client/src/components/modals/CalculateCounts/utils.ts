import type { TFunction } from "i18next";
import type { Moment } from "moment";
import moment from "moment";

import type { Range } from "./CountsRangesButtons";

export function predefinedRanges(t: TFunction<string>): Range[] {
    const forDay = (name: string, moment: () => Moment): Range => ({
        name: name,
        from: () => moment().startOf("day"),
        to: () => moment().endOf("day"),
    });

    return [
        {
            name: t("calculateCounts.range.lastHour", "Last hour"),
            from: () => moment().subtract(1, "hour"),
            to: () => moment(),
        },
        forDay(t("calculateCounts.range.today", "Today"), () => moment()),
        forDay(t("calculateCounts.range.yesterday", "Yesterday"), () => moment().subtract(1, "day")),
        forDay(t("calculateCounts.range.dayBeforeYesterday", "Day before yesterday"), () => moment().subtract(2, "day")),
        forDay(t("calculateCounts.range.thisDayLastWeek", "This day last week"), () => moment().subtract(7, "day")),
    ];
}
