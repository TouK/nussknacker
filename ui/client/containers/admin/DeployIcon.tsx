import React, {useCallback} from "react"
import {Glyphicon} from "react-bootstrap"
import * as DialogMessages from "../../common/DialogMessages"
import ProcessStateUtils from "../../components/Process/ProcessStateUtils"
import HttpService from "../../http/HttpService"
import {useWindows} from "../../windowManager"

export function DeployIcon({processState, onDeploy, process}: {processState: any, onDeploy: () => void, process: any}): JSX.Element | null {
  const {confirm} = useWindows()

  const onClick = useCallback(
    () => confirm({
      text: DialogMessages.deploy(process.name),
      onConfirmCallback: () => HttpService.deploy(process.name).finally(onDeploy),
    }),
    [confirm, process.name, onDeploy],
  )

  if (!ProcessStateUtils.canDeploy(processState)) {
    return null
  }
  return <Glyphicon glyph="play" title="Deploy scenario" onClick={onClick}/>
}
