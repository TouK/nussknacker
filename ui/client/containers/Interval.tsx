import {PropsWithChildren, useEffect} from "react"

export function useInterval(action: () => void, {refreshTime, ignoreFirst}: {refreshTime: number, ignoreFirst?: boolean}): void {
  useEffect(() => {
    // eslint-disable-next-line no-unused-expressions
    if (!ignoreFirst) {
      action()
    }
  }, [action])
  useEffect(() => {
    const interval = setInterval(() => action(), refreshTime)
    return () => clearInterval(interval)
  }, [refreshTime, action])
}

export function Interval(props: PropsWithChildren<{time: number, action: () => void}>): typeof props.children {
  useInterval(props.action, {refreshTime: props.time})
  return props.children
}

