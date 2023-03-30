import {useEffect, useState} from "react"

export function useStateWithRevertTimeout<T>(startValue: T, time = 10000): ReturnType<typeof useState<T>> {
  const [defaultValue] = useState<T>(startValue)
  const [value, setValue] = useState<T>(defaultValue)

  useEffect(() => {
    let t
    if (value && time) {
      t = setTimeout(() => {
        setValue(defaultValue)
      }, time)
    }
    return () => clearTimeout(t)
  }, [value, time, defaultValue])

  return [value, setValue]
}
