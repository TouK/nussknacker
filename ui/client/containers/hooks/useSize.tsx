import React, {ForwardedRef, useCallback, useMemo} from "react"
import useDimensions, {Options} from "react-cool-dimensions"

(async () => {
  if (!("ResizeObserver" in window)) {
    const {ResizeObserver, ResizeObserverEntry} = await import("@juggle/resize-observer")
    window.ResizeObserver = ResizeObserver
    window.ResizeObserverEntry = ResizeObserverEntry as any // Only use it when you have this trouble: https://github.com/wellyshen/react-cool-dimensions/issues/45
  }
})()

export const useSize: typeof useDimensions = options => {
  return useDimensions(options)
}

function assignRef<T>(ref: ForwardedRef<T>, value: T) {
  if (typeof ref === "function") {
    ref(value)
  } else if (ref != null) {
    ref.current = value
  }
}

export function useSizeWithRef<T extends HTMLElement | null = HTMLElement>(
  ref: ForwardedRef<T>,
  options?: Options<T>,
): ReturnType<typeof useSize> {
  const dimensions = useSize<T>(options)
  const {observe} = dimensions
  const observeWithRef = useCallback(
    (el: T) => {
      observe(el)
      assignRef(ref, el)
    },
    [observe, ref],
  )
  return useMemo(
    () => ({...dimensions, observe: observeWithRef}),
    [dimensions, observeWithRef],
  )
}
