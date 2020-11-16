import {useSelector} from "react-redux"
import {getBaseIntervalTime} from "../../reducers/selectors/settings"
import {useInterval} from "../Interval"

export function useIntervalRefresh(getProcesses: () => Promise<void>) {
  const refreshTime = useSelector(getBaseIntervalTime)
  useInterval(getProcesses, {refreshTime})
}
