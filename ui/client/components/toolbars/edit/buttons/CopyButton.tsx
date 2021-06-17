import React from "react"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/copy.svg"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"

function CopyButton(): JSX.Element {
  const {copy} = useSelectionActions()
  const {t} = useTranslation()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-copy.button", "copy")}
      icon={<Icon/>}
      disabled={!copy}
      onClick={copy ? (event => copy(event.nativeEvent)) : null}
    />
  )
}

export default CopyButton

