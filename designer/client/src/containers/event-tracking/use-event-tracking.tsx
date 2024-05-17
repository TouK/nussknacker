import { debounce } from "lodash";
import httpService from "../../http/HttpService";
import { useCallback } from "react";
import { EventTrackingSelector, EventTrackingType, getEventStatisticName } from "./helpers";
import { useSelector } from "react-redux";
import { getFeatureSettings } from "../../reducers/selectors/settings";

export type TrackEventParams = { selector: EventTrackingSelector; event: EventTrackingType };

export const useEventTracking = () => {
    const featuresSettings = useSelector(getFeatureSettings);
    const areStatisticsEnabled = featuresSettings.usageStatisticsReports.enabled;

    const trackEvent = async (trackEventParams: TrackEventParams) => {
        if (!areStatisticsEnabled) {
            return;
        }
        await httpService.sendStatistics([{ name: getEventStatisticName(trackEventParams) }]);
    };

    const trackEventWithDebounce = useCallback(
        debounce((event: TrackEventParams) => trackEvent(event), 1500),
        [],
    );

    return { trackEvent, trackEventWithDebounce };
};
