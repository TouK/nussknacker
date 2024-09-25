import { useDispatch, useSelector } from "react-redux";
import { getProcessName, getScenarioGraph, isProcessRenamed } from "../../../reducers/selectors/graph";
import { useEffect } from "react";
import { fetchActivityFormParameters } from "../../../actions/nk";

export function useActivityCapabilities() {
    const dispatch = useDispatch();

    const isRenamed = useSelector(isProcessRenamed);
    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);

    useEffect(() => {
        if (isRenamed) return;
        dispatch(fetchActivityFormParameters(scenarioName, scenarioGraph));
    }, [dispatch, isRenamed, scenarioName, scenarioGraph]);
}
