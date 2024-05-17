import { EventTrackingType, removeEventSelectors } from "./helpers";
import { useDocumentEventListener } from "rooks";
import { useEventTracking } from "./use-event-tracking";

export const useRegisterTrackingEvents = () => {
    const { trackEvent, trackEventWithDebounce } = useEventTracking();

    useDocumentEventListener("click", function (event: Event) {
        const path = event.composedPath() as HTMLElement[];
        for (const element of path) {
            const eventName = element.dataset?.statisticEvent as EventTrackingType;

            if (eventName?.startsWith("CLICK")) {
                trackEvent({ type: eventName });
                break;
            }

            if (eventName?.startsWith("FILTER") || eventName?.startsWith("SORT")) {
                const selected = (element as HTMLOptionElement).selected;
                if (selected) {
                    trackEvent({ type: eventName });
                }
                break;
            }
        }
    });

    useDocumentEventListener("keyup", function (event: KeyboardEvent) {
        const path = event.composedPath() as HTMLElement[];
        for (const element of path) {
            const eventName = element.dataset?.statisticEvent as EventTrackingType;

            if (eventName?.startsWith("SEARCH")) {
                trackEventWithDebounce({ type: eventName });
                break;
            }
        }
    });
};
