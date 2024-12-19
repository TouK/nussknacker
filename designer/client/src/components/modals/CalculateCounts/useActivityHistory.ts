import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import HttpService from "../../../http/HttpService";
import { DATE_FORMAT } from "../../../config";
import { Range } from "./CountsRangesButtons";
import moment from "moment";
import { PredefinedActivityType } from "../../toolbars/activities/types";

function displayableNameOfPredefinedActivityType(predefinedActivityType: PredefinedActivityType) {
    switch (predefinedActivityType) {
        case PredefinedActivityType.ScenarioCanceled:
            return "Cancel";
        case PredefinedActivityType.ScenarioDeployed:
            return "Deployment";
        case PredefinedActivityType.PerformedScheduledExecution:
            return "Scheduled deployment";
        case PredefinedActivityType.PerformedSingleExecution:
            return "Run now";
        default:
            return "Unknown activity type";
    }
}

export function useActivityHistory(processName: string, processingMode: string): Range[] {
    const { t } = useTranslation();
    const [activities, setActivities] = useState<Range[]>([]);

    useEffect(() => {
        HttpService.fetchProcessesActivities(processName)
            .then((activities) =>
                processingMode.includes("batch")
                    ? activities.filter((activity) => activity.type !== PredefinedActivityType.ScenarioDeployed)
                    : activities,
            )
            .then((activities) =>
                activities.map((current, i, all) => {
                    const from = moment(current.date);
                    const to = all[i - 1]?.date;
                    const isOmitted = current.type === PredefinedActivityType.ScenarioCanceled;
                    return {
                        from: () => from,
                        to: () => (to ? moment(to) : moment().add(1, "day").startOf("day")),
                        name: t("calculateCounts.range.prevAction", "{{activity}} {{date}}", {
                            activity: displayableNameOfPredefinedActivityType(current.type),
                            date: from.format(DATE_FORMAT),
                        }),
                        isOmitted,
                    };
                }),
            )
            .then((res) => res.filter((activity) => !activity.isOmitted))
            .then((res) => {
                res[0].name = t("calculateCounts.range.lastAction", "Latest Run");
                return res;
            })
            .then(setActivities);
    }, [t, processName]);

    return activities;
}
