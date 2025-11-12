import { useEffect, useMemo } from "react";

import { Initiator, startLiveData, stopLiveData } from "../../actions/nk/liveData";
import { VisibleDataType } from "../../reducers/graph/types";
import { getHasPauseReasons, getVisibleDataType, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getHasOpenedNodeWindows } from "../../reducers/selectors/getWindowsIdMapping";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";

export function useLiveDataIfNeeded() {
    const dispatch = useAppDispatch();

    const visibleDataType = useAppSelector(getVisibleDataType);
    useEffect(() => {
        if (visibleDataType === VisibleDataType.test || visibleDataType === VisibleDataType.counts) {
            dispatch(stopLiveData(Initiator.tests));
        }
    }, [dispatch, visibleDataType]);

    const settings = useAppSelector(getUserSettings);
    const autoEnableLiveData = settings["scenario.autoEnableLiveData"];
    const readyForResults = useAppSelector(isReadyForLiveData);
    const hasOpenedNodeWindow = useAppSelector(getHasOpenedNodeWindows);
    const hasPauseReasons = useAppSelector(getHasPauseReasons);

    const shouldStart = useMemo(() => {
        return autoEnableLiveData && readyForResults && !hasOpenedNodeWindow && !hasPauseReasons;
    }, [autoEnableLiveData, hasOpenedNodeWindow, hasPauseReasons, readyForResults]);

    useEffect(() => {
        if (shouldStart) {
            dispatch(startLiveData());
        }
    }, [dispatch, shouldStart]);
}
