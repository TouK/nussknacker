import React from "react"
import {RootState} from "../../../../../reducers/index"
import {useSelector, useDispatch} from "react-redux"
import HttpService from "../../../../../http/HttpService"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {toggleProcessActionDialog} from "../../../../../actions/nk/toggleProcessActionDialog"
import {ToolbarButton} from "../../../ToolbarButton"
import {isCancelPossible, getProcessId} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../../UserRightPanel"
import {loadProcessState} from "../../../../../actions/nk/process"

type Props = Pick<PassedProps,
  | "isStateLoaded"
  | "processState">

export default function CancelDeployButton(props: Props) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const cancelPossible = useSelector<RootState>(state => isCancelPossible(state, props))
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

