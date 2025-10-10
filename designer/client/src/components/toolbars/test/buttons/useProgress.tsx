import { useCallback, useState } from "react";
import { useIntervalWhen } from "rooks";

import type { RefreshData } from "../../../../actions/nk/displayProcessCounts";

export function useProgress(refresh: RefreshData, when: boolean): [percent: number, reset: () => void] {
    const [percent, setPercent] = useState(0);

    useIntervalWhen(
        () => {
            const percent = Math.round(((refresh.last + refresh.nextIn - Date.now()) / refresh.nextIn) * 100);
            setPercent(percent);
        },
        200,
        refresh && when,
    );

    const reset = useCallback(() => setPercent(0), []);

    return [percent, reset];
}
