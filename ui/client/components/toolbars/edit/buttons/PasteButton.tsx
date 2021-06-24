import React from "react"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/paste.svg"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"

function PasteButton(): JSX.Element {
  const {t} = useTranslation()

  const {paste, canPaste} = useSelectionActions()
  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-paste.button", "paste")}
      icon={<Icon/>}
      disabled={!paste || !canPaste}
      onClick={paste ? event => paste(event.nativeEvent) : null}
    />
  )
}

export default PasteButton
