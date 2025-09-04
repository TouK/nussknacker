import { useDocumentEventListener } from "rooks";

import { getFeatureSettings } from "../../reducers/selectors/settings";
import { useAppSelector } from "../../store/storeHelpers";
import type { EventTrackingSelectorType } from "./event-tracking-selector";
import {
    ClickEventsSelector,
    EventTrackingType,
    FilterEventsSelector,
    SearchEventsSelector,
    SortEventsSelector,
} from "./event-tracking-selector";
import { useEventTracking } from "./use-event-tracking";

export const useRegisterTrackingEvents = () => {
    const { trackEvent, trackEventWithDebounce } = useEventTracking();
    const featuresSettings = useAppSelector(getFeatureSettings);
    const isEnabledForStatisticsEvent = (eventName: keyof DocumentEventMap) =>
        featuresSettings.usageStatisticsReports.enabled ? eventName : undefined;

    useDocumentEventListener(isEnabledForStatisticsEvent("click"), function (event: Event) {
        const path = event.composedPath() as HTMLElement[];

        for (const element of path) {
            const selector = element.dataset?.selector as EventTrackingSelectorType;

            if (Object.values(ClickEventsSelector).find((clickEvent) => clickEvent === selector)) {
                trackEvent({ selector, event: EventTrackingType.Click });
                break;
            }

            if (Object.values(FilterEventsSelector).find((filterEvent) => filterEvent === selector)) {
                const selected = (element as HTMLOptionElement).selected;
                if (selected) {
                    trackEvent({ selector, event: EventTrackingType.Filter });
                }
                break;
            }

            if (Object.values(SortEventsSelector).find((sortEvent) => sortEvent === selector)) {
                const selected = (element as HTMLOptionElement).selected;
                if (selected) {
                    trackEvent({ selector, event: EventTrackingType.Sort });
                }
                break;
            }
        }
    });

    useDocumentEventListener(isEnabledForStatisticsEvent("keyup"), function (event: KeyboardEvent) {
        const path = event.composedPath() as HTMLElement[];
        for (const element of path) {
            const selector = element.dataset?.selector as EventTrackingSelectorType;

            if (Object.values(SearchEventsSelector).find((searchEvent) => searchEvent === selector)) {
                const value = (element as HTMLInputElement).value;

                if (value) {
                    trackEventWithDebounce({ selector, event: EventTrackingType.Search });
                }

                break;
            }
        }
    });
};
