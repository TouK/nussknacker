import { useCallback } from "react";
import { useEventTracking, EventTrackingType } from "nussknackerUi/eventTracking";

export const useTrackFilterSelect = () => {
    const { trackEvent } = useEventTracking();

    const withTrackFilterSelect: <Value = unknown>(
        { type }: { type: EventTrackingType },
        callback: (value: Value[]) => (value: Value) => void,
    ) => (value: Value[], isEnabled: boolean) => unknown = useCallback(
        ({ type }: { type: EventTrackingType }, callback) => {
            return (value, isEnabled = true) => {
                if (isEnabled) {
                    trackEvent({ type });
                }
                return callback(value);
            };
        },
        [trackEvent],
    );

    return { withTrackFilterSelect };
};
