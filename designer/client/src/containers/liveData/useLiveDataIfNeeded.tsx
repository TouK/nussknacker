import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";

import { fetchAndDisplayLiveData, stopLiveData } from "../../actions/nk/liveData";
import { useUserSettings } from "../../common/userSettings";
import { getLiveDataWasEnabled, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getHasOpenedNodeWindows } from "../../reducers/selectors/getWindowsIdMapping";

export function useLiveDataIfNeeded() {
    const dispatch = useDispatch();
    const [settings] = useUserSettings();
    const readyForResults = useSelector(isReadyForLiveData);
    const hasOpenedNodeWindow = useSelector(getHasOpenedNodeWindows);
    const autoEnableLiveData = settings["scenario.autoEnableLiveData"];
    const liveDataWasEnabled = useSelector(getLiveDataWasEnabled);
    const liveDataIsAutoEnabledOrWasManuallyEnabled = autoEnableLiveData || (!!autoEnableLiveData && liveDataWasEnabled);

    useEffect(() => {
        if (hasOpenedNodeWindow) {
            dispatch(stopLiveData());
        } else if (readyForResults && liveDataIsAutoEnabledOrWasManuallyEnabled) {
            dispatch(fetchAndDisplayLiveData());
        }
    }, [dispatch, hasOpenedNodeWindow, readyForResults, liveDataWasEnabled, liveDataIsAutoEnabledOrWasManuallyEnabled]);
}
