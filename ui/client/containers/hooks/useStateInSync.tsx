import {isEqual} from "lodash"
import {useEffect, useState} from "react"

export const useStateInSync = <T extends any>(value: T): [T, React.Dispatch<React.SetStateAction<T>>] => {
  const [inputValue, setInputValue] = useState(value)
  useEffect(
    () => {
      if (!isEqual(value, inputValue)) {
        setInputValue(value)
      }
    },
    [value],
  )
  return [inputValue, setInputValue]
}
