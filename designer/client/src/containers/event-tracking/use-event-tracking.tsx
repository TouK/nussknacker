import { debounce } from "lodash";
import httpService from "../../http/HttpService";
import { useCallback } from "react";
import { EventTrackingType } from "./helpers";

export type TrackEventParams = { type: EventTrackingType };
export const useEventTracking = () => {
    const trackEvent = async ({ type }: TrackEventParams) => {
        await httpService.sendStatistics([{ name: type }]);
    };

    const trackEventWithDebounce = useCallback(
        debounce((event: TrackEventParams) => trackEvent(event), 1500),
        [],
    );

    return { trackEvent, trackEventWithDebounce };
};
