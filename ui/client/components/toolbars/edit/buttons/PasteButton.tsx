import React from "react"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/paste.svg"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {ToolbarButtonProps} from "../../types"

function PasteButton(props: ToolbarButtonProps): JSX.Element {
  const {t} = useTranslation()
  const {paste, canPaste} = useSelectionActions()
  const {disabled} = props
  const available = !disabled && paste && canPaste

  return (
    <CapabilitiesToolbarButton
      editFrontend
      name={t("panels.actions.edit-paste.button", "paste")}
      icon={<Icon/>}
      disabled={available}
      onClick={available ? event => paste(event.nativeEvent) : null}
    />
  )
}

export default PasteButton
