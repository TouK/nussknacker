import { EventTrackingSelector, EventTrackingType } from "./helpers";
import { useDocumentEventListener } from "rooks";
import { useEventTracking } from "./use-event-tracking";
import { useSelector } from "react-redux";
import { getFeatureSettings } from "../../reducers/selectors/settings";

export const useRegisterTrackingEvents = () => {
    const { trackEvent, trackEventWithDebounce } = useEventTracking();
    const featuresSettings = useSelector(getFeatureSettings);
    const isEnabledForStatisticsEvent = (eventName: keyof DocumentEventMap) =>
        featuresSettings.usageStatisticsReports.enabled ? eventName : undefined;

    useDocumentEventListener(isEnabledForStatisticsEvent("click"), function (event: Event) {
        const path = event.composedPath() as HTMLElement[];
        for (const element of path) {
            const selector = element.dataset?.selector as EventTrackingSelector;
            const event = element.dataset?.statisticEvent as unknown as EventTrackingType;

            if (event === EventTrackingType.CLICK) {
                trackEvent({ selector, event });
                break;
            }

            if (event === EventTrackingType.FILTER || event === EventTrackingType.SORT) {
                const selected = (element as HTMLOptionElement).selected;
                if (selected) {
                    trackEvent({ selector, event });
                }
                break;
            }
        }
    });

    useDocumentEventListener(isEnabledForStatisticsEvent("keyup"), function (event: KeyboardEvent) {
        const path = event.composedPath() as HTMLElement[];
        for (const element of path) {
            const selector = element.dataset?.selector as EventTrackingSelector;
            const event = element.dataset?.statisticEvent as unknown as EventTrackingType;

            if (event === EventTrackingType.SEARCH) {
                trackEventWithDebounce({ selector, event });
                break;
            }
        }
    });
};
