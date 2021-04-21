import React, {useMemo} from "react"
import {useSelector} from "react-redux"
import {getProcessId, getProcessState} from "../../../reducers/selectors/graph"
import {getCustomActions} from "../../../reducers/selectors/settings"
import CustomActionButton from "../../toolbars/status/buttons/CustomActionButton"

export interface ActionButtonProps {
  name: string,
}

export function ActionButton({name}: ActionButtonProps): JSX.Element {
  const processId = useSelector(getProcessId)
  const status = useSelector(getProcessState)?.status
  const customActions = useSelector(getCustomActions)

  const action = useMemo(
    () => customActions.find(a => a.name === name),
    [customActions, name],
  )

  return action ? <CustomActionButton action={action} processId={processId} processStatus={status}/> : null
}
