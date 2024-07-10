import { useSelector } from "react-redux";
import { getFeatureSettings } from "../reducers/selectors/settings";
import { useCallback, useEffect, useState } from "react";
import httpService from "../http/HttpService";
import { useLocalstorageState } from "rooks";
import moment from "moment";

const getCacheExpirationDate = (createdAt: number | null, statisticCacheTime: number) =>
    moment(createdAt).add(statisticCacheTime, "minute");
const isCacheExpired = (statisticsCache: { createdAt?: number }, statisticCacheTime: number) => {
    const cacheExpirationDate = getCacheExpirationDate(statisticsCache?.createdAt, statisticCacheTime);
    const currentDate = moment();

    if (!statisticsCache) {
        return true;
    }

    return cacheExpirationDate.isSameOrBefore(currentDate);
};

export const STATISTICS_CACHE = "NU_STATISTICS_CACHE";
const STATISTIC_CACHE_TIME_IN_MINUTES = 60;

const useStatisticsCache = (statisticCacheTime: number) => {
    const [statisticsCache, setStatisticsCache] = useLocalstorageState<{ createdAt?: number }>(STATISTICS_CACHE, null);

    const [statisticsReadyToRefetch, setStatisticsReadyToRefetch] = useState(() => {
        return isCacheExpired(statisticsCache, statisticCacheTime);
    });

    useEffect(() => {
        if (statisticsReadyToRefetch) {
            setStatisticsCache({ createdAt: moment().valueOf() });
            setStatisticsReadyToRefetch(false);
        }
    }, [setStatisticsCache, statisticsReadyToRefetch]);

    useEffect(() => {
        const remainingTimeToCacheExpired = moment(getCacheExpirationDate(statisticsCache?.createdAt, statisticCacheTime)).diff(
            moment(),
            "milliseconds",
        );

        const timer = setInterval(() => {
            if (isCacheExpired(statisticsCache, statisticCacheTime)) {
                setStatisticsReadyToRefetch(true);
            }
        }, remainingTimeToCacheExpired);

        return () => clearInterval(timer);
    }, [statisticCacheTime, statisticsCache]);

    return { statisticsReadyToRefetch };
};

export function useAnonymousStatistics(statisticCacheTime = STATISTIC_CACHE_TIME_IN_MINUTES) {
    const featuresSettings = useSelector(getFeatureSettings);
    const { statisticsReadyToRefetch } = useStatisticsCache(statisticCacheTime);

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

        httpService.fetchStatisticUsage().then((response) => {
            response.data.urls.forEach((url) => {
                setTimeout(() => {
                    appendStatisticElement(url);
                }, 500);
            });
        });
    }, []);

    useEffect(() => {
        if (statisticsReadyToRefetch && featuresSettings.usageStatisticsReports.enabled) {
            handleUsageStatistics();
        }
    }, [featuresSettings.usageStatisticsReports.enabled, handleUsageStatistics, statisticsReadyToRefetch]);
}
