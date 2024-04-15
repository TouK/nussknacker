import { useSelector } from "react-redux";
import { getFeatureSettings } from "../reducers/selectors/settings";
import { useCallback } from "react";
import { useInterval } from "./Interval";
import httpService from "../http/HttpService";

const STATISTIC_FETCH_TIME_IN_MINUTES = 60;

export function useAnonymousStatistics(statisticFetchTime = STATISTIC_FETCH_TIME_IN_MINUTES) {
    const featuresSettings = useSelector(getFeatureSettings);

    const handleUsageStatistics = useCallback(() => {
        const appendStatisticElement = (url: string) => {
            const usageStatistics = document.getElementById("usage-statistics");

            const usageStatisticsImage = document.createElement("img");
            usageStatisticsImage.id = "usage-statistics";
            usageStatisticsImage.src = url;
            usageStatisticsImage.alt = "anonymous usage reporting";
            usageStatisticsImage.referrerPolicy = "origin";
            usageStatisticsImage.hidden = true;

            if (usageStatistics) {
                usageStatistics.replaceWith(usageStatisticsImage);
            } else {
                document.body.insertAdjacentElement("afterend", usageStatisticsImage);
            }
        };

        httpService.fetchStatisticUrls().then((response) => {
            response.data.urls.forEach((url) => {
                setTimeout(() => {
                    appendStatisticElement(url);
                }, 500);
            });
        });
    }, []);

    useInterval(handleUsageStatistics, {
        ignoreFirst: false,
        refreshTime: 60 * 1000 * statisticFetchTime,
        disabled: !featuresSettings.usageStatisticsReports.enabled,
    });
}
