import React, {useCallback} from "react"
import {Glyphicon} from "react-bootstrap"
import {useDispatch} from "react-redux"
import {toggleConfirmDialog} from "../../actions/nk"
import * as DialogMessages from "../../common/DialogMessages"
import ProcessStateUtils from "../../components/Process/ProcessStateUtils"
import HttpService from "../../http/HttpService"

export function CancelIcon({processState, onCancel, process}: {processState: any, onCancel: () => void, process: any}) {
  const dispatch = useDispatch()
  const cancel = useCallback(
    () => {
      dispatch(toggleConfirmDialog(
        true,
        DialogMessages.stop(process.name),
        () => HttpService.cancel(process.name).finally(onCancel),
      ))
    },
    [process.name, onCancel],
  )

  if (!ProcessStateUtils.canCancel(processState)) {
    return null
  }
  return <Glyphicon glyph="stop" title="Cancel scenario" onClick={cancel}/>
}
