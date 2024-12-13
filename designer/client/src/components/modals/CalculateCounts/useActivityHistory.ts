import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import HttpService from "../../../http/HttpService";
import { DATE_FORMAT } from "../../../config";
import { Range } from "./CountsRangesButtons";
import moment from "moment";

export function useActivityHistory(processName: string): Range[] {
    const { t } = useTranslation();
    const [activities, setActivities] = useState<Range[]>([]);

    useEffect(() => {
        HttpService.fetchProcessesActivities(processName)
            .then((activities) =>
                activities.map((current, i, all) => {
                    const from = moment(current.date);
                    console.log(activities);
                    const to = all[i - 1]?.date;
                    return {
                        from: () => from,
                        to: () => (to ? moment(to) : moment().add(1, "day").startOf("day")),
                        name: i
                            ? t("calculateCounts.range.prevAction", "Previous {{activity}} #{{i}} {{date}}", {
                                  activity: current.type,
                                  i: all.length - i,
                                  date: from.format(DATE_FORMAT),
                              })
                            : t("calculateCounts.range.lastDeploy", "Latest deploy"),
                    };
                }),
            )
            .then(setActivities);
    }, [t, processName]);

    return activities;
}
