import { useEffect } from "react";
import { useSelector } from "react-redux";

import { useGraph } from "../../components/graph/GraphContext";
import { getLiveDataRefresh } from "../../reducers/selectors/getLiveData";

export const CLASS_NAME = "live-data";

export function useLiveDataRefreshEnabled() {
    const liveDataRefresh = useSelector(getLiveDataRefresh);
    const graphGetter = useGraph();

    useEffect(() => {
        const paper = graphGetter()?.processGraphPaper.el;
        paper?.classList.toggle(CLASS_NAME, Boolean(liveDataRefresh?.nextIn));
    }, [graphGetter, liveDataRefresh]);
}
