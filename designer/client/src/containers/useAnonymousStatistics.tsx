import { useSelector } from "react-redux";
import { getFeatureSettings } from "../reducers/selectors/settings";
import { useCallback, useEffect, useState } from "react";
import httpService from "../http/HttpService";
import { useLocalstorageState } from "rooks";
import moment from "moment";

const getLockReleaseDate = (createdAt: number | null, statisticLockReleaseTime: number) =>
    moment(createdAt).add(statisticLockReleaseTime, "minute");
const isLockReleased = (statisticsLock: { createdAt?: number }, statisticLockReleaseTime: number) => {
    const lockReleaseDate = getLockReleaseDate(statisticsLock?.createdAt, statisticLockReleaseTime);
    const currentDate = moment();

    if (!statisticsLock) {
        return true;
    }

    return lockReleaseDate.isSameOrBefore(currentDate);
};

export const STATISTICS_LOCK = "NU_STATISTICS_LOCK";
const STATISTIC_LOCK_RELEASE_IN_MINUTES = 60;

const useStatisticsLock = (statisticLockReleaseTime: number) => {
    const [statisticsLock, setStatisticsLock] = useLocalstorageState<{ createdAt?: number }>(STATISTICS_LOCK, null);

    const [statisticsReadyToRefetch, setStatisticsReadyToRefetch] = useState(() => {
        return isLockReleased(statisticsLock, statisticLockReleaseTime);
    });

    useEffect(() => {
        if (statisticsReadyToRefetch) {
            setStatisticsLock({ createdAt: moment().valueOf() });
            setStatisticsReadyToRefetch(false);
        }
    }, [setStatisticsLock, statisticsReadyToRefetch]);

    useEffect(() => {
        const remainingLogReleaseTime = moment(getLockReleaseDate(statisticsLock?.createdAt, statisticLockReleaseTime)).diff(
            moment(),
            "milliseconds",
        );

        const timer = setInterval(() => {
            if (isLockReleased(statisticsLock, statisticLockReleaseTime)) {
                setStatisticsReadyToRefetch(true);
            }
        }, remainingLogReleaseTime);

        return () => clearInterval(timer);
    }, [statisticLockReleaseTime, statisticsLock]);

    return statisticsReadyToRefetch;
};

export function useAnonymousStatistics(statisticLockReleaseTime = STATISTIC_LOCK_RELEASE_IN_MINUTES) {
    const featuresSettings = useSelector(getFeatureSettings);
    const statisticsReadyToRefetch = useStatisticsLock(statisticLockReleaseTime);

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
