import { useEffect, useMemo } from "react";

import { Initiator, startLiveData, stopLiveData } from "../../actions/nk/liveData";
import { VisibleDataType } from "../../reducers/graph/types";
import { getVisibleDataType, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
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

    const shouldStart = useMemo(() => {
        return autoEnableLiveData && readyForResults && !hasOpenedNodeWindow;
    }, [autoEnableLiveData, hasOpenedNodeWindow, readyForResults]);

    useEffect(() => {
        if (shouldStart) {
            dispatch(startLiveData(Initiator.init));
        }
    }, [dispatch, shouldStart]);
}
