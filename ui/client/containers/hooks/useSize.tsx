import useDimensions from "react-cool-dimensions"

(async () => {
  if (!("ResizeObserver" in window)) {
    const module = await import("@juggle/resize-observer")
    // eslint-disable-next-line i18next/no-literal-string
    window["ResizeObserver"] = module.ResizeObserver
    // Only use it when you have this trouble: https://github.com/wellyshen/react-cool-dimensions/issues/45
    // window.ResizeObserverEntry = module.ResizeObserverEntry;
  }
})()

export const useSize: typeof useDimensions = options => {
  return useDimensions(options)
}
