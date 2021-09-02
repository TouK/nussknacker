import cn from "classnames"
import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/save.svg"
import {getProcessName, getProcessUnsavedNewName, isProcessRenamed, isSaveDisabled} from "../../../../reducers/selectors/graph"
import {getCapabilities} from "../../../../reducers/selectors/other"
import {useWindows} from "../../../../windowManager"
import {WindowKind} from "../../../../windowManager/WindowKind"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ToolbarButtonProps} from "../../types"
import classes from "./SaveButton.styl"

function SaveButton(props: ToolbarButtonProps): JSX.Element {
  const {t} = useTranslation()
  const {disabled} = props
  const capabilities = useSelector(getCapabilities)
  const saveDisabled = useSelector(isSaveDisabled)

  const processName = useSelector(getProcessName)
  const unsavedNewName = useSelector(getProcessUnsavedNewName)
  const isRenamed = useSelector(isProcessRenamed)
  const title = isRenamed ?
    t("saveProcess.renameTitle", "Save scenario as {{name}}", {name: unsavedNewName}) :
    t("saveProcess.title", "Save scenario {{name}}", {name: processName})

  const {open} = useWindows()
  const onClick = () => open({
    title,
    isModal: true,
    shouldCloseOnEsc: true,
    kind: WindowKind.saveProcess,
  })

  const available = !disabled && !saveDisabled && capabilities.write

  return (
    <ToolbarButton
      name={t("panels.actions.process-save.button", "save")}
      icon={<Icon/>}
      labelClassName={cn("button-label", !disabled && !saveDisabled && classes.unsaved)}
      disabled={!available}
      onClick={onClick}
    />
  )
}

export default SaveButton
