import React from "react"
import {useSelector, useDispatch} from "react-redux"
import HttpService from "../../../../../http/HttpService"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {toggleProcessActionDialog} from "../../../../../actions/nk/toggleProcessActionDialog"
import ToolbarButton from "../../../ToolbarButton"
import {isCancelPossible, getProcessId} from "../../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {loadProcessState} from "../../../../../actions/nk/process"

export default function CancelDeployButton() {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const cancelPossible = useSelector(isCancelPossible)
  const processId = useSelector(getProcessId)

  return (
    <ToolbarButton
      name={t("panels.actions.deploy-canel.button", "cancel")}
      disabled={!cancelPossible}
      icon={InlinedSvgs.buttonCancel}
      onClick={() => dispatch(toggleProcessActionDialog(
        t("panels.actions.deploy-canel.dialog", "Cancel process"),
        (p, c) => HttpService.cancel(p, c).finally(() => dispatch(loadProcessState(processId))),
        false,
      ))}
    />
  )
}

