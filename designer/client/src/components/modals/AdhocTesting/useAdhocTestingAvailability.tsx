import { useDispatch, useSelector } from "react-redux";
import {
    getProcessName,
    getScenarioGraph,
    getTestCapabilities,
    isLatestProcessVersion,
    isProcessRenamed,
} from "../../../reducers/selectors/graph";
import { useEffect, useMemo } from "react";
import { displayTestCapabilities, fetchTestFormParameters } from "../../../actions/nk";

// TODO: fetch TestCapabilities and TestFormParameters in chain to avoid stupid errors
export function useAdhocTestingAvailability(disabled: boolean) {
    const dispatch = useDispatch();

    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const testCapabilities = useSelector(getTestCapabilities);
    const isRenamed = useSelector(isProcessRenamed);
    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);

    const isAvailable = useMemo(() => {
        return !disabled && processIsLatestVersion && testCapabilities?.canTestWithForm;
    }, [disabled, processIsLatestVersion, testCapabilities?.canTestWithForm]);

    useEffect(() => {
        if (isRenamed) return;
        dispatch(displayTestCapabilities(scenarioName, scenarioGraph));
    }, [dispatch, isRenamed, scenarioGraph, scenarioName]);

    useEffect(() => {
        if (isRenamed || !isAvailable) return;
        dispatch(fetchTestFormParameters(scenarioName, scenarioGraph));
    }, [dispatch, isRenamed, scenarioName, scenarioGraph, isAvailable]);

    return isAvailable;
}
