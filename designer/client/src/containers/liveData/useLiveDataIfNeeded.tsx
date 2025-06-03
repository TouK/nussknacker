import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";

import { fetchAndDisplayLiveData, stopLiveData } from "../../actions/nk/liveData";
import { isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getHasOpenedNodeWindows } from "../../reducers/selectors/getWindowsIdMapping";

export function useLiveDataIfNeeded() {
    const dispatch = useDispatch();
    const readyForResults = useSelector(isReadyForLiveData);
    const hasOpenedNodeWindow = useSelector(getHasOpenedNodeWindows);

    useEffect(() => {
        if (hasOpenedNodeWindow) {
            dispatch(stopLiveData());
        } else if (readyForResults) {
            dispatch(fetchAndDisplayLiveData());
        }
    }, [dispatch, hasOpenedNodeWindow, readyForResults]);
}
