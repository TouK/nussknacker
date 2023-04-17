import {ToolbarWrapper} from "../toolbarComponents/ToolbarWrapper"
import ProcessComments from "../ProcessComments"
import React from "react"
import {useTranslation} from "react-i18next"

export function CommentsPanel() {
  const {t} = useTranslation()

  return (
    <ToolbarWrapper id="comments-panel" title={t("panels.comments.title", "Comments")}>
      <ProcessComments/>
    </ToolbarWrapper>
  )
}
