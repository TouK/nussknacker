import cn from "classnames"
import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {toggleModalDialog} from "../../../../actions/nk"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/save.svg"
import {isSaveDisabled} from "../../../../reducers/selectors/graph"
import {getCapabilities} from "../../../../reducers/selectors/other"
import Dialogs from "../../../modals/Dialogs"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import classes from "./SaveButton.styl"

function SaveButton(): JSX.Element {
  const {t} = useTranslation()
  const capabilities = useSelector(getCapabilities)
  const saveDisabled = useSelector(isSaveDisabled)
  const dispatch = useDispatch()

  return (
    <ToolbarButton
      name={t("panels.actions.process-save.button", "save")}
      icon={<Icon/>}
      labelClassName={cn("button-label", !saveDisabled && classes.unsaved)}
      disabled={saveDisabled || !capabilities.write}
      onClick={() =>
        //TODO: Checking permission to archive should be done by check action from state - we should add new action type
        dispatch(toggleModalDialog(Dialogs.types.saveProcess))
      }
    />
  )
}

export default SaveButton
