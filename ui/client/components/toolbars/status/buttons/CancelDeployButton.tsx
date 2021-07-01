import React from "react"
import {useSelector, useDispatch} from "react-redux"
import HttpService from "../../../../http/HttpService"
import {toggleProcessActionDialog} from "../../../../actions/nk/toggleProcessActionDialog"
import {getCapabilities} from "../../../../reducers/selectors/other"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {isCancelPossible, getProcessId} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {loadProcessState} from "../../../../actions/nk/process"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/stop.svg"
import {ToolbarButtonProps} from "../../types"

export default function CancelDeployButton(props: ToolbarButtonProps) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const {disabled} = props
  const cancelPossible = useSelector(isCancelPossible)
  const processId = useSelector(getProcessId)
  const capabilities = useSelector(getCapabilities)
  const available = !disabled && cancelPossible && capabilities.deploy

  return (
    <ToolbarButton
      name={t("panels.actions.deploy-canel.button", "cancel")}
      disabled={!available}
      icon={<Icon/>}
      onClick={() => dispatch(toggleProcessActionDialog(
        t("panels.actions.deploy-canel.dialog", "Cancel process"),
        (p, c) => HttpService.cancel(p, c).finally(() => dispatch(loadProcessState(processId))),
        false,
      ))}
    />
  )
}

