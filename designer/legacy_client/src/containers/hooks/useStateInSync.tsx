import {isEqual} from "lodash"
import {Dispatch, SetStateAction, useEffect, useState} from "react"

export function useStateInSync<T>(value: T): [T, Dispatch<SetStateAction<T>>] {
  const [inputValue, setInputValue] = useState(value)
  useEffect(
    () => {
      setInputValue(input => !isEqual(value, input) ? value : input)
    },
    [value],
  )
  return [inputValue, setInputValue]
}
