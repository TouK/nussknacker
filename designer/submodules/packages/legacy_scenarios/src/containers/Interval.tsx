import {useEffect} from "react"

export function useInterval(action: () => void, {
  refreshTime,
  ignoreFirst,
}: { refreshTime: number, ignoreFirst?: boolean }): void {
  useEffect(() => {
    if (!ignoreFirst) {
      action()
    }
  }, [ignoreFirst, action])
  useEffect(() => {
    const interval = setInterval(() => action(), refreshTime)
    return () => clearInterval(interval)
  }, [refreshTime, action])
}

