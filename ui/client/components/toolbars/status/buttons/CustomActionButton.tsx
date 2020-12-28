import React from "react"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton";
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/custom_action.svg";
import {loadProcessState, toggleProcessActionDialog} from "../../../../actions/nk";
import {disableToolTipsHighlight, enableToolTipsHighlight} from "../../../../actions/nk/tooltips"
import HttpService from "../../../../http/HttpService";
import {useTranslation} from "react-i18next";
import {useDispatch, useSelector} from "react-redux";
import {getProcessId, hasError, isDeployPossible, isSaveDisabled} from "../../../../reducers/selectors/graph";
import {getCapabilities} from "../../../../reducers/selectors/other";

type Props = {
  actionName: string
  // ,
  // deployPolicy: boolean
}

export default function CustomActionButton(props: Props) {

  const { actionName } = props
  const dispatch = useDispatch()
  const {t} = useTranslation()

  const deployPossible = useSelector(isDeployPossible)
  const saveDisabled = useSelector(isSaveDisabled)
  const capabilities = useSelector(getCapabilities)
  const processId = useSelector(getProcessId)
  const hasErrors = useSelector(hasError)

  const customActionToolTip = !capabilities.deploy ?
    t("panels.actions.deploy.tooltips.forbidden", "Action forbidden for current process.") :
    hasErrors ?
      t("panels.actions.deploy.tooltips.error", "Cannot execute action due to errors. Please look at the left panel for more details.") :
      !saveDisabled ?
        t("panels.actions.deploy.tooltips.unsaved", "You have unsaved changes.") :
        null

  const actionMouseOver = hasErrors ? () => dispatch(enableToolTipsHighlight()) : null
  const actionMouseOut = hasErrors ? () => dispatch(disableToolTipsHighlight()) : null

  return <ToolbarButton
    name={actionName}
    disabled={!(deployPossible && capabilities.deploy)}
    icon={<Icon/>}
    title={customActionToolTip}
    onClick={() => dispatch(toggleProcessActionDialog(
      actionName,
      (_p, _c) => HttpService.customAction(processId, actionName).finally(() => dispatch(loadProcessState(processId))),
      true,
    ))}
    onMouseOver={actionMouseOver}
    onMouseOut={actionMouseOut}
  />
}