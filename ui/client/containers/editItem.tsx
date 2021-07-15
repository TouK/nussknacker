import React from "react"
import {useTranslation} from "react-i18next"
import {ProcessType} from "../components/Process/types"
import TableRowIcon from "../components/table/TableRowIcon"
import {ProcessLink} from "./processLink"

export function EditItem({process}: {process: ProcessType}) {
  const {t} = useTranslation()
  return (
    <ProcessLink processId={process.name}>
      <TableRowIcon glyph="edit" title={t("tableRowIcon-edit", "Edit scenario")}/>
    </ProcessLink>
  )
}

export function ShowItem({process}: {process: ProcessType}) {
  const {t} = useTranslation()

  const title = process.isSubprocess ?
    t("tableRowIcon-show-subprocess", "Show fragment") :
    t("tableRowIcon-show", "Show scenario")

  return (
    <ProcessLink processId={process.name}>
      <TableRowIcon glyph="eye-open" title={title}/>
    </ProcessLink>
  )
}
