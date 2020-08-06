import React, {useCallback} from "react"
import {ProcessType} from "../components/Process/types"
import HttpService from "../http/HttpService"
import {AsyncInput} from "./AsyncInput"

type OwnProps = {
  process: ProcessType,
  onChange: (current: string, next: string) => void | Promise<void>,
}

export function ProcessNameInput({process, onChange}: OwnProps) {
  const onChangeHandler = useCallback(
    async (name) => {
      const isSuccess = await HttpService.changeProcessName(process.name, name)
      if (isSuccess) {
        await onChange(process.name, name)
      }
      return isSuccess
    },
    [process.name, onChange],
  )

  return (
    <AsyncInput
      className="transparent"
      value={process.name}
      onChangeAsync={onChangeHandler}
    />
  )
}
