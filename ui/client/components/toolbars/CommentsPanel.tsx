import {CollapsibleToolbar} from "../toolbarComponents/CollapsibleToolbar"
import ProcessComments from "../ProcessComments"
import React from "react"
import {useTranslation} from "react-i18next"

export function CommentsPanel() {
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="COMMENTS-PANEL" title={t("panels.comments.title", "Comments")}>
      <ProcessComments/>
    </CollapsibleToolbar>
  )
}
