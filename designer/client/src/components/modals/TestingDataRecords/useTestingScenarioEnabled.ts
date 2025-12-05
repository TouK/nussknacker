import { TestCapabilityStatus } from "../../../common/TestResultUtils";
import { getTestCapabilities, isLatestProcessVersion } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import { useAdhocTestingAvailability } from "../AdhocTesting/useAdhocTestingAvailability";

interface Props {
    disabled: boolean;
}

export const useTestingScenarioEnabled = ({ disabled }: Props) => {
    // Availability of adhoc testing
    const adhocTestIsAvailable = useAdhocTestingAvailability(disabled);

    // Availability of live data testing
    const testCapabilities = useAppSelector(getTestCapabilities);
    const processIsLatestVersion = useAppSelector(isLatestProcessVersion);
    const testFromLiveDataIsAvailable =
        !disabled && processIsLatestVersion && testCapabilities?.testWithLiveData.status === TestCapabilityStatus.AVAILABLE;

    return adhocTestIsAvailable || testFromLiveDataIsAvailable;
};
