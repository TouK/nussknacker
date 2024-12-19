import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import HttpService from "../../../http/HttpService";
import { DATE_FORMAT } from "../../../config";
import { Range } from "./CountsRangesButtons";
import moment from "moment";
import { ActivityTypesRelatedToExecutions } from "../../toolbars/activities/types";

function displayableNameOfPredefinedActivityType(predefinedActivityType: ActivityTypesRelatedToExecutions) {
    switch (predefinedActivityType) {
        case ActivityTypesRelatedToExecutions.ScenarioCanceled:
            return "Cancel";
        case ActivityTypesRelatedToExecutions.ScenarioDeployed:
            return "Deployment";
        case ActivityTypesRelatedToExecutions.PerformedScheduledExecution:
            return "Scheduled deployment";
        case ActivityTypesRelatedToExecutions.PerformedSingleExecution:
            return "Run now";
        default:
            return "Unknown activity type";
    }
}

export function useActivityHistory(processName: string, processingMode: string): Range[] {
    const { t } = useTranslation();
    const [activities, setActivities] = useState<Range[]>([]);

    useEffect(() => {
        HttpService.fetchActivitiesRelatedToExecutions(processName)
            .then((activities) =>
                processingMode.includes("batch")
                    ? activities.filter((activity) => activity.type !== ActivityTypesRelatedToExecutions.ScenarioDeployed)
                    : activities,
            )
            .then((activities) =>
                activities.map((current, i, all) => {
                    const from = moment(current.date);
                    const to = all[i - 1]?.date;
                    const isOmitted = current.type === ActivityTypesRelatedToExecutions.ScenarioCanceled;
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
