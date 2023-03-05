import React, {useCallback} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/unarchive.svg"
import * as DialogMessages from "../../../../common/DialogMessages"
import HttpService from "../../../../http/HttpService"
import {getProcessId, isArchived} from "../../../../reducers/selectors/graph"
import {useWindows} from "../../../../windowManager"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {ToolbarButtonProps} from "../../types"
import {displayCurrentProcessVersion, loadProcessToolbarsConfiguration} from "../../../../actions/nk"

function UnArchiveButton({disabled}: ToolbarButtonProps) {
  const processId = useSelector(getProcessId)
  const archived = useSelector(isArchived)
  const available = !disabled || !archived
  const {t} = useTranslation()
  const {confirm} = useWindows()
  const dispatch = useDispatch()

  const onClick = useCallback(() => available && confirm(
    {
      text: DialogMessages.unArchiveProcess(processId),
      onConfirmCallback: (confirmed) => confirmed && HttpService.unArchiveProcess(processId).then(() => {
        dispatch(loadProcessToolbarsConfiguration(processId))
        dispatch(displayCurrentProcessVersion(processId))
      }),
      confirmText: t("panels.actions.process-unarchive.yes", "Yes"),
      denyText: t("panels.actions.process-unarchive.no", "No"),
    },
  ), [available, confirm, dispatch, processId, t])

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
