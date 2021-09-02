import React, {MutableRefObject, RefCallback, useEffect} from "react"

export function useCombinedRefs<T>(...refs: Array<RefCallback<T> | MutableRefObject<T> | null>) {
  const targetRef = React.useRef<T>()

  useEffect(() => {
    refs.forEach(ref => {
      if (!ref) return

      if (typeof ref === "function") {
        ref(targetRef.current)
      } else {
        ref.current = targetRef.current
      }
    })
  }, [refs])

  return targetRef
}
