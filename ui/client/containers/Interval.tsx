import {PropsWithChildren, useEffect} from "react"

export function useInterval(action: () => void, ms: number) {
  useEffect(() => { action() }, [action])
  useEffect(() => {
    const interval = setInterval(() => action(), ms)
    return () => clearInterval(interval)
  }, [ms, action])
}

export function Interval(props: PropsWithChildren<{time: number, action: () => void}>) {
  useInterval(props.action, props.time)
  return props.children
}

