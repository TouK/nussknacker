import React from "react"
import {useTranslation} from "react-i18next"
import {ButtonWithFocus} from "../../../withFocus"

export function RemoveButton({onClick}: {onClick: () => void}): JSX.Element {
  const {t} = useTranslation()
  return (
    <ButtonWithFocus
      className="addRemoveButton"
      title={t("node.row.remove.title", "Remove field")}
      onClick={onClick}
    >
      {t("node.row.remove.text", "-")}
    </ButtonWithFocus>
  )
}
