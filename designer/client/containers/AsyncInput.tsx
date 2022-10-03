import React, {useCallback, useRef, useState} from "react"
import {InputWithFocus} from "../components/withFocus"
import {useStateInSync} from "./hooks/useStateInSync"

type Keys = "onEnter" | string

const keyEvents = <Event extends React.KeyboardEvent<HTMLInputElement>>(keymap: Record<Keys, (event: Event) => void>) => {
  return (event: Event) => {
    return keymap[`on${event.key}`]?.(event)
  }
}

export function AsyncInput({value, onChangeAsync, ...props}: {
  value: string,
  onChangeAsync: (value: string) => Promise<boolean>,
  className?: string,
}) {
  const ref = useRef<HTMLInputElement>()

  const [isBusy, setIsBusy] = useState(false)
  const [inputValue, setInputValue] = useStateInSync(value)

  const changeValue = useCallback(({target}) => setInputValue(target.value), [])

  const onEnter = useCallback(
    async () => {
      setIsBusy(true)
      if (inputValue === value || await onChangeAsync(inputValue)) {
        ref.current.blur()
      }
      setIsBusy(false)
    },
    [value, inputValue, onChangeAsync],
  )

  const revertValue = useCallback(() => isBusy || setInputValue(value), [isBusy, value])

  return (
    <InputWithFocus
      {...props}
      value={inputValue}
      ref={ref}
      onKeyPress={keyEvents({onEnter})}
      onChange={changeValue}
      onBlur={revertValue}
    />
  )
}
