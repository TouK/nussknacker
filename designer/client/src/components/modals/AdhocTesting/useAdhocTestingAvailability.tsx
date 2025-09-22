import { useEffect, useMemo } from "react";
import { useDebouncedValue } from "rooks";

import { displayTestCapabilities } from "../../../actions/nk/process";
import { TestCapabilityStatus } from "../../../common/TestResultUtils";
import {
    getProcessName,
    getScenarioGraph,
    getTestCapabilities,
    isLatestProcessVersion,
    isProcessRenamed,
} from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";

// TODO: fetch TestCapabilities and TestFormParameters in chain to avoid stupid errors
export function useAdhocTestingAvailability(disabled: boolean) {
    const dispatch = useAppDispatch();

    const processIsLatestVersion = useAppSelector(isLatestProcessVersion);
    const testCapabilities = useAppSelector(getTestCapabilities);
    const isRenamed = useAppSelector(isProcessRenamed);
    const scenarioName = useAppSelector(getProcessName);
    const _scenarioGraph = useAppSelector(getScenarioGraph);

    const isAvailable = useMemo(() => {
        return !disabled && processIsLatestVersion && testCapabilities?.testWithParameters.status === TestCapabilityStatus.AVAILABLE;
    }, [disabled, processIsLatestVersion, testCapabilities?.testWithParameters.status]);

    const [scenarioGraph] = useDebouncedValue(_scenarioGraph, 500);
    useEffect(() => {
        if (isRenamed) return;
        dispatch(displayTestCapabilities(scenarioName, scenarioGraph));
    }, [dispatch, isRenamed, scenarioGraph, scenarioName]);

    return isAvailable;
}
