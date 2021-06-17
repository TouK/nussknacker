import React from "react"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/delete.svg"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"

function DeleteButton(): JSX.Element {
  const {t} = useTranslation()
  const {delete: remove} = useSelectionActions()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-delete.button", "delete")}
      icon={<Icon/>}
      disabled={!remove}
      onClick={remove ? e => remove(e.nativeEvent) : null}
    />
  )
}

export default DeleteButton
