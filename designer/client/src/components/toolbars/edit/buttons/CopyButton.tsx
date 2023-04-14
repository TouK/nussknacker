import React from "react"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/copy.svg"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {ToolbarButtonProps} from "../../types"

function CopyButton(props: ToolbarButtonProps): JSX.Element {
  const {copy} = useSelectionActions()
  const {t} = useTranslation()
  const {disabled} = props
  const available = !disabled && copy

  return (
    <CapabilitiesToolbarButton
      editFrontend
      name={t("panels.actions.edit-copy.button", "copy")}
      icon={<Icon/>}
      disabled={!available}
      onClick={available ? event => copy(event.nativeEvent) : null}
    />
  )
}

export default CopyButton

