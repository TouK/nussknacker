import { debounce } from "lodash";
import { useCallback, useMemo } from "react";

import httpService from "../../http/HttpService/instance";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import { useAppSelector } from "../../store/storeHelpers";
import { getEventStatisticName } from "./helpers";
import type { EventTrackingSelectorType, EventTrackingType } from "./use-register-tracking-events";

export type TrackEventParams = { selector: EventTrackingSelectorType; event: EventTrackingType };

export const useEventTracking = () => {
    const featuresSettings = useAppSelector(getFeatureSettings);
    const areStatisticsEnabled = featuresSettings.usageStatisticsReports.enabled;

    const trackEvent = useCallback(
        async (trackEventParams: TrackEventParams) => {
            if (!areStatisticsEnabled) {
                return;
            }
            await httpService.sendStatistics([{ name: getEventStatisticName(trackEventParams) }]);
        },
        [areStatisticsEnabled],
    );

    const trackEventWithDebounce = useMemo(() => debounce((event: TrackEventParams) => trackEvent(event), 1500), [trackEvent]);

    return { trackEvent, trackEventWithDebounce };
};
