import React from "react"
import {useDispatch, useSelector} from "react-redux"
import HttpService from "../../../../../http/HttpService"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {disableToolTipsHighlight, enableToolTipsHighlight} from "../../../../../actions/nk/tooltips"
import {toggleProcessActionDialog} from "../../../../../actions/nk/toggleProcessActionDialog"
import ToolbarButton from "../../../toolbars/ToolbarButton"
import {isDeployPossible, isSaveDisabled, hasError, getProcessId} from "../../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {loadProcessState} from "../../../../../actions/nk/process"

export default function DeployButton() {
  const dispatch = useDispatch()
  const deployPossible = useSelector(isDeployPossible)
  const saveDisabled = useSelector(isSaveDisabled)
  const hasErrors = useSelector(hasError)
  const processId = useSelector(getProcessId)

  const {t} = useTranslation()
  const deployToolTip = hasErrors ?
    t("panels.actions.deploy.tooltips.error", "Cannot deploy due to errors. Please look at the left panel for more details.") :
    !saveDisabled ?
      t("panels.actions.deploy.tooltips.unsaved", "You have unsaved changes.") :
      null
  const deployMouseOver = hasErrors ? () => dispatch(enableToolTipsHighlight()) : null
  const deployMouseOut = hasErrors ? () => dispatch(disableToolTipsHighlight()) : null

  return (
    <ToolbarButton
      name={t("panels.actions.deploy.button", "deploy")}
      disabled={!deployPossible}
      icon={InlinedSvgs.buttonDeploy}
      title={deployToolTip}
      onClick={() => dispatch(toggleProcessActionDialog(
        t("panels.actions.deploy.dialog", "Deploy process"),
        (p, c) => HttpService.deploy(p, c).finally(() => dispatch(loadProcessState(processId))),
        true,
      ))}
      onMouseOver={deployMouseOver}
      onMouseOut={deployMouseOut}
    />
  )
}
