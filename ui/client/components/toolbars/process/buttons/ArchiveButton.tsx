import React, {useCallback} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/archive.svg"
import * as DialogMessages from "../../../../common/DialogMessages"
import {ArchiveTabData} from "../../../../containers/Archive"
import history from "../../../../history"
import HttpService from "../../../../http/HttpService"
import {getProcessId, isArchivePossible} from "../../../../reducers/selectors/graph"
import {useWindows} from "../../../../windowManager"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {ToolbarButtonProps} from "../../types"

function ArchiveButton({disabled}: ToolbarButtonProps): JSX.Element {
  const processId = useSelector(getProcessId)
  const archivePossible = useSelector(isArchivePossible)
  const available = !disabled && archivePossible
  const {t} = useTranslation()
  const {confirm} = useWindows()

  const onClick = useCallback(() => available && confirm(
    {
      text: DialogMessages.archiveProcess(processId),
      onConfirmCallback: () => HttpService.archiveProcess(processId).then(() => history.push(ArchiveTabData.path)),
      confirmText: t("panels.actions.process-archive.yes", "Yes"),
      denyText: t("panels.actions.process-archive.no", "No"),
    },
    {category: events.categories.rightPanel, action: events.actions.buttonClick, name: `archive`},
  ), [available, confirm, processId, t])

  return (
    <CapabilitiesToolbarButton
      change
      name={t("panels.actions.process-archive.button", "archive")}
      icon={<Icon/>}
      disabled={!available}
      onClick={onClick}
    />
  )
}

export default ArchiveButton
