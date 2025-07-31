import { useEffect, useMemo } from "react";

import { displayTestCapabilities } from "../../../actions/nk";
import { TestCapabilityStatus } from "../../../common/TestResultUtils";
import {
    getProcessName,
    getScenarioGraph,
    getTestCapabilities,
    isLatestProcessVersion,
    isProcessRenamed,
} from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/configureStore";

// TODO: fetch TestCapabilities and TestFormParameters in chain to avoid stupid errors
export function useAdhocTestingAvailability(disabled: boolean) {
    const dispatch = useAppDispatch();

    const processIsLatestVersion = useAppSelector(isLatestProcessVersion);
    const testCapabilities = useAppSelector(getTestCapabilities);
    const isRenamed = useAppSelector(isProcessRenamed);
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const isAvailable = useMemo(() => {
        return !disabled && processIsLatestVersion && testCapabilities?.testWithParameters.status === TestCapabilityStatus.AVAILABLE;
    }, [disabled, processIsLatestVersion, testCapabilities?.testWithParameters.status]);

    useEffect(() => {
        if (isRenamed) return;
        dispatch(displayTestCapabilities(scenarioName, scenarioGraph));
    }, [dispatch, isRenamed, scenarioGraph, scenarioName]);

    return isAvailable;
}
