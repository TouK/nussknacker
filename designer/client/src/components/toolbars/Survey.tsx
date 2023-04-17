import {useTranslation} from "react-i18next"
import {useNkTheme} from "../../containers/theme"
import React, {useCallback} from "react"
import {ToolbarWrapper} from "../toolbarComponents/ToolbarWrapper"
import {DragHandle} from "../toolbarComponents/DragHandle"
import {Button, Stack, Typography} from "@mui/material"
import {useWindows, WindowKind} from "../../windowManager"
import {useSurvey} from "./useSurvey"

function Survey(): JSX.Element {
  const {t} = useTranslation()
  const {theme} = useNkTheme()
  const [survey, hideSurvey] = useSurvey()

  const {open} = useWindows()
  const onOpen = useCallback(
    () => survey && open({
      kind: WindowKind.survey,
      meta: survey.link,
      isResizable: true,
      shouldCloseOnEsc: false,
      width: 750,
      height: 900,
    }),
    [open, survey]
  )

  if (!survey) {
    return null
  }

  return (
    <ToolbarWrapper onClose={hideSurvey} color={theme.colors.accent}>
      <DragHandle>
        <Stack p={1} spacing={.5}>
          <Typography variant="body2">{survey.text}</Typography>
          <Stack direction="row" spacing={1}>
            <Button size="small" variant="text" onClick={onOpen}>{t("panels.survey.ok", "let's go!")}</Button>
            <Button size="small" variant="text" onClick={hideSurvey}>{t("panels.survey.no", "close")}</Button>
          </Stack>
        </Stack>
      </DragHandle>
    </ToolbarWrapper>
  )
}

export default Survey
