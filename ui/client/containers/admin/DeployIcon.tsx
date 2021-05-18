import React, {useCallback} from "react"
import {Glyphicon} from "react-bootstrap"
import {useDispatch} from "react-redux"
import {toggleConfirmDialog} from "../../actions/nk"
import * as DialogMessages from "../../common/DialogMessages"
import ProcessStateUtils from "../../components/Process/ProcessStateUtils"
import HttpService from "../../http/HttpService"

export function DeployIcon({processState, onDeploy, process}: {processState: any, onDeploy: () => void, process: any}) {
  const dispatch = useDispatch()
  const onClick = useCallback(
    () => {
      dispatch(toggleConfirmDialog(
        true,
        DialogMessages.deploy(process.name),
        () => HttpService.deploy(process.name).finally(onDeploy),
      ))
    },
    [process, onDeploy],
  )

  if (!ProcessStateUtils.canDeploy(processState)) {
    return null
  }
  return <Glyphicon glyph="play" title="Deploy scenario" onClick={onClick}/>
}
