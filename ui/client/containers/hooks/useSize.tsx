import useDimensions from "react-cool-dimensions"

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
