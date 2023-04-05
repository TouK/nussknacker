import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {loadProcessState} from "../../../../actions/nk"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/stop.svg"
import HttpService from "../../../../http/HttpService"
import {getProcessId, isCancelPossible, isSaveDisabled} from "../../../../reducers/selectors/graph"
import {getCapabilities} from "../../../../reducers/selectors/other"
import {useWindows} from "../../../../windowManager"
import {WindowKind} from "../../../../windowManager/WindowKind"
import {ToggleProcessActionModalData} from "../../../modals/DeployProcessDialog"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ToolbarButtonProps} from "../../types"

export default function CancelDeployButton(props: ToolbarButtonProps) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const {disabled} = props
  const cancelPossible = useSelector(isCancelPossible)
  const saveDisabled = useSelector(isSaveDisabled)
  const processId = useSelector(getProcessId)
  const capabilities = useSelector(getCapabilities)
  const available = !disabled && cancelPossible && capabilities.deploy

  const cancelToolTip = !saveDisabled ? t("panels.actions.deploy-cancel.tooltips.unsaved", "You have unsaved changes.") : null

  const {open} = useWindows()
  const action = (p, c) => HttpService.cancel(p, c).finally(() => dispatch(loadProcessState(processId)))
  const message = t("panels.actions.deploy-cancel.dialog", "Cancel scenario {{name}}", {name: processId})

  return (
    <ToolbarButton
      name={t("panels.actions.deploy-cancel.button", "cancel")}
      disabled={!available}
      icon={<Icon/>}
      title={cancelToolTip}
      onClick={() => open<ToggleProcessActionModalData>({
        title: message,
        kind: WindowKind.deployProcess,
        meta: {action},
      })}
    />
  )
}

