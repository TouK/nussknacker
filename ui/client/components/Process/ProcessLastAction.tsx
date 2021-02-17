import React from "react"
import {ProcessActionType, ProcessType} from "./types"
import {formatRelatively} from "../../common/DateUtils"

import {Glyphicon} from "react-bootstrap"
import {useTranslation} from "react-i18next"

type Props = {
  process: ProcessType,
}

export default function ProcessLastAction(props: Props): JSX.Element {
  const {t} = useTranslation()
  const {process} = props

  const processAction = (lastAction: ProcessActionType): JSX.Element => {
    const performedAt = formatRelatively(lastAction.performedAt)
    const user = lastAction.user

    return (
      <span title={t("processLastAction.title", "Performed by {{user}} at {{performedAt}}.", {performedAt, user})}>
        {lastAction.action}
      </span>
    )
  }

  return process?.lastAction ?
    processAction(process.lastAction) :
    <Glyphicon glyph="minus" title={t("processLastAction.noActionTitle", "No action occurred.")}/>
}
