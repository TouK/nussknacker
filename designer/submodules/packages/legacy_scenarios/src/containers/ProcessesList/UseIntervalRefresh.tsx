import {useBaseIntervalTime} from "../../reducers/selectors/settings"
import {useInterval} from "../Interval"

export function useIntervalRefresh(getProcesses: () => Promise<void>) {
  const refreshTime = useBaseIntervalTime()
  useInterval(getProcesses, {refreshTime})
}
