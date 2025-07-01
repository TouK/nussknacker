import { useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";

import { Initiator, startLiveData, stopLiveData } from "../../actions/nk/liveData";
import { useUserSettings } from "../../common/userSettings";
import { VisibleDataType } from "../../reducers/graph";
import { getHasPauseReasons, getVisibleDataType, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getHasOpenedNodeWindows } from "../../reducers/selectors/getWindowsIdMapping";

export function useLiveDataIfNeeded() {
    const dispatch = useDispatch();

    const visibleDataType = useSelector(getVisibleDataType);
    useEffect(() => {
        if (visibleDataType === VisibleDataType.test || visibleDataType === VisibleDataType.counts) {
            dispatch(stopLiveData(Initiator.tests));
        }
    }, [dispatch, visibleDataType]);

    const [settings] = useUserSettings();
    const autoEnableLiveData = settings["scenario.autoEnableLiveData"];
    const readyForResults = useSelector(isReadyForLiveData);
    const hasOpenedNodeWindow = useSelector(getHasOpenedNodeWindows);
    const hasPauseReasons = useSelector(getHasPauseReasons);

    const shouldStart = useMemo(() => {
        return autoEnableLiveData && readyForResults && !hasOpenedNodeWindow && !hasPauseReasons;
    }, [autoEnableLiveData, hasOpenedNodeWindow, hasPauseReasons, readyForResults]);

    useEffect(() => {
        if (shouldStart) {
            dispatch(startLiveData());
        }
    }, [dispatch, shouldStart]);
}
