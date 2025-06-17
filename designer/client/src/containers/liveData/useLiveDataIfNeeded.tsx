import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";

import { startLiveData, stopLiveData } from "../../actions/nk/liveData";
import { useUserSettings } from "../../common/userSettings";
import { getHasPauseReasons, getVisibleDataType, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getHasOpenedNodeWindows } from "../../reducers/selectors/getWindowsIdMapping";

export function useLiveDataIfNeeded() {
    const dispatch = useDispatch();
    const [settings] = useUserSettings();
    const readyForResults = useSelector(isReadyForLiveData);
    const hasOpenedNodeWindow = useSelector(getHasOpenedNodeWindows);
    const autoEnableLiveData = settings["scenario.autoEnableLiveData"];
    const visibleDataType = useSelector(getVisibleDataType);

    useEffect(() => {
        if (visibleDataType === "test" || visibleDataType === "counts") {
            dispatch(stopLiveData("tests"));
        }
    }, [dispatch, visibleDataType]);

    const hasPauseReasons = useSelector(getHasPauseReasons);
    useEffect(() => {
        if (autoEnableLiveData && readyForResults && !hasOpenedNodeWindow && !hasPauseReasons) {
            dispatch(startLiveData());
        }
    }, [autoEnableLiveData, dispatch, hasOpenedNodeWindow, readyForResults, hasPauseReasons]);
}
