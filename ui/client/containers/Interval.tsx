import {PropsWithChildren, useEffect} from "react"

export function useInterval(action: () => void, {refreshTime, ignoreFirst}: {refreshTime: number, ignoreFirst?: boolean}): void {
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

export function Interval(props: PropsWithChildren<{time: number, action: () => void}>): typeof props.children {
  useInterval(props.action, {refreshTime: props.time})
  return props.children
}

