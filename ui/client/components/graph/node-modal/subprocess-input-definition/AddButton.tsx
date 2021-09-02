import React from "react"
import {useTranslation} from "react-i18next"
import {ButtonWithFocus} from "../../../withFocus"
import {useFieldsContext} from "./NodeRowFields"

export function AddButton(): JSX.Element {
  const {t} = useTranslation()
  const {add} = useFieldsContext()
  return (
    <ButtonWithFocus
      className="addRemoveButton"
      title={t("node.row.add.title", "Add field")}
      onClick={add}
    >
      {t("node.row.add.text", "+")}
    </ButtonWithFocus>
  )
}
