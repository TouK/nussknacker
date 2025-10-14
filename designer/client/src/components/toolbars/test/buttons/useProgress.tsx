import { useCallback, useState } from "react";
import { useIntervalWhen } from "rooks";

import type { RefreshData } from "../../../../actions/nk/displayProcessCounts";

export function useProgress(refresh: RefreshData, when = true): { percent?: number; reset: () => void; isProgressing: boolean } {
    const [percent, setPercent] = useState<number>();

    const next = refresh ? refresh.last + refresh.nextIn : 0;
    const isEnabled = when && next > Date.now();

    useIntervalWhen(
        () => {
            const percent = Math.round(((next - Date.now()) / refresh.nextIn) * 100);
            setPercent(percent);
        },
        200,
        isEnabled,
    );

    const reset = useCallback(() => setPercent(0), []);

    return { percent, reset, isProgressing: isEnabled && percent > 0 };
}
