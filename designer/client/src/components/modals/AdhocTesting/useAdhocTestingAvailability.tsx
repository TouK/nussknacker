import { useEffect, useMemo } from "react";
import { useSelector } from "react-redux";

import { displayTestCapabilities } from "../../../actions/nk";
import { TestCapabilityStatus } from "../../../common/TestResultUtils";
import {
    getProcessName,
    getScenarioGraph,
    getTestCapabilities,
    isLatestProcessVersion,
    isProcessRenamed,
} from "../../../reducers/selectors/graph";
import { useAppDispatch } from "../../../store/configureStore";

// TODO: fetch TestCapabilities and TestFormParameters in chain to avoid stupid errors
export function useAdhocTestingAvailability(disabled: boolean) {
    const dispatch = useAppDispatch();

    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const testCapabilities = useSelector(getTestCapabilities);
    const isRenamed = useSelector(isProcessRenamed);
    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);

    const isAvailable = useMemo(() => {
        return !disabled && processIsLatestVersion && testCapabilities?.testWithParameters.status === TestCapabilityStatus.AVAILABLE;
    }, [disabled, processIsLatestVersion, testCapabilities?.testWithParameters.status]);

    useEffect(() => {
        if (isRenamed) return;
        dispatch(displayTestCapabilities(scenarioName, scenarioGraph));
    }, [dispatch, isRenamed, scenarioGraph, scenarioName]);

    return isAvailable;
}
