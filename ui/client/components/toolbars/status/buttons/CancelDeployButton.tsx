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

export default function CancelDeployButton() {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const cancelPossible = useSelector(isCancelPossible)
  const processId = useSelector(getProcessId)
  const capabilities = useSelector(getCapabilities)

  return (
    <ToolbarButton
      name={t("panels.actions.deploy-canel.button", "cancel")}
      disabled={!(cancelPossible && capabilities.deploy)}
      icon={<Icon/>}
      onClick={() => dispatch(toggleProcessActionDialog(
        t("panels.actions.deploy-canel.dialog", "Cancel scenario"),
        (p, c) => HttpService.cancel(p, c).finally(() => dispatch(loadProcessState(processId))),
        false,
      ))}
    />
  )
}

