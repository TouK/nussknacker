import React, {useCallback} from "react"
import {Glyphicon} from "react-bootstrap"
import * as DialogMessages from "../../common/DialogMessages"
import ProcessStateUtils from "../../components/Process/ProcessStateUtils"
import HttpService from "../../http/HttpService"
import {useWindows} from "../../windowManager"

export function CancelIcon({processState, onCancel, process}: {processState: any, onCancel: () => void, process: any}): JSX.Element | null {
  const {confirm} = useWindows()

  const cancel = useCallback(
    () => confirm({
      text: DialogMessages.stop(process.name),
      onConfirmCallback: () => HttpService.cancel(process.name).finally(onCancel),
    }),
    [confirm, process.name, onCancel],
  )

  if (!ProcessStateUtils.canCancel(processState)) {
    return null
  }
  return <Glyphicon glyph="stop" title="Cancel scenario" onClick={cancel}/>
}
