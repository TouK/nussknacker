import useLocalStorageState from "@mui/utils/useLocalStorageState";

const mockEnabledKey = (scenarioName: string, nodeId: string) => `mock.enabled.${scenarioName}.${nodeId}`;

export function readMockEnabled(scenarioName: string, nodeId: string): boolean {
    return localStorage.getItem(mockEnabledKey(scenarioName, nodeId)) !== "false";
}

export function useMockEnabled(scenarioName: string, nodeId: string): [boolean, (enabled: boolean) => void] {
    const [stored, setStored] = useLocalStorageState(mockEnabledKey(scenarioName, nodeId), "true");
    return [stored !== "false", (enabled: boolean) => setStored(enabled ? "true" : "false")];
}
