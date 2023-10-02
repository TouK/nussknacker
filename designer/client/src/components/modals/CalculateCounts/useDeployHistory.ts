import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import HttpService from "../../../http/HttpService";
import { DATE_FORMAT } from "../../../config";
import { Range } from "./CountsRangesButtons";
import moment from "moment";

export function useDeployHistory(processId: string): Range[] {
    const { t } = useTranslation();
    const [deploys, setDeploys] = useState<Range[]>([]);

    useEffect(() => {
        HttpService.fetchProcessesDeployments(processId)
            .then((dates) =>
                dates.map((current, i, all) => {
                    const from = moment(current);
                    const to = all[i - 1];
                    return {
                        from: () => from,
                        to: () => (to ? moment(to) : moment().add(1, "day").startOf("day")),
                        name: i
                            ? t("calculateCounts.range.prevDeploy", "Previous deploy #{{i}} ({{date}})", {
                                  i: all.length - i,
                                  date: from.format(DATE_FORMAT),
                              })
                            : t("calculateCounts.range.lastDeploy", "Latest deploy"),
                    };
                }),
            )
            .then(setDeploys);
    }, [t, processId]);

    return deploys;
}
