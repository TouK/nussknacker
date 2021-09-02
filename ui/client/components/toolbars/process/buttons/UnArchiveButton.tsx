import React, {useCallback} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/unarchive.svg"
import * as DialogMessages from "../../../../common/DialogMessages"
import {ProcessesTabData} from "../../../../containers/Processes"
import {SubProcessesTabData} from "../../../../containers/SubProcesses"
import history from "../../../../history"
import HttpService from "../../../../http/HttpService"
import {getProcessId, isArchived, isSubprocess} from "../../../../reducers/selectors/graph"
import {useWindows} from "../../../../windowManager"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {ToolbarButtonProps} from "../../types"

function UnArchiveButton({disabled}: ToolbarButtonProps) {
  const processId = useSelector(getProcessId)
  const archived = useSelector(isArchived)
  const available = !disabled || !archived
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const {confirm} = useWindows()

  const redirectPath = isSubprocess ? ProcessesTabData.path : SubProcessesTabData.path

  const onClick = useCallback(() => available && confirm(
    {
      text: DialogMessages.unArchiveProcess(processId),
      onConfirmCallback: () => HttpService.unArchiveProcess(processId).then(() => history.push(redirectPath)),
      confirmText: t("panels.actions.process-unarchive.yes", "Yes"),
      denyText: t("panels.actions.process-unarchive.no", "No"),
    },
    {category: events.categories.rightPanel, action: events.actions.buttonClick, name: `unarchive`},
  ), [available, confirm, processId, redirectPath, t])

  return (
    <CapabilitiesToolbarButton
      change
      name={t("panels.actions.process-unarchive.button", "unarchive")}
      icon={<Icon/>}
      disabled={!available}
      onClick={onClick}
    />
  )
}

export default UnArchiveButton
