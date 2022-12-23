import {useTranslation} from "react-i18next"
import {useUserSettings} from "../../common/userSettings"
import {useNkTheme} from "../../containers/theme"
import React, {useCallback} from "react"
import {ToolbarWrapper} from "../toolbarComponents/ToolbarWrapper"
import {DragHandle} from "../toolbarComponents/DragHandle"
import {Button, Stack, Typography} from "@mui/material"
import {SURVEY_CLOSED_SETTINGS_KEY} from "./SurveyPanel"
import {useWindows, WindowKind} from "../../windowManager"

interface SurveyProps {
  text: string,
  link: string,
}

function Survey({text, link}: SurveyProps): JSX.Element {
  const {t} = useTranslation()
  const {theme} = useNkTheme()

  const [userSettings, , setSettings] = useUserSettings()
  const onClose = useCallback(
    () => setSettings({...userSettings, [SURVEY_CLOSED_SETTINGS_KEY]: true}),
    [setSettings, userSettings]
  )

  const {open} = useWindows()
  const onOpen = useCallback(
    () => open({
      title: "Survey",
      kind: WindowKind.survey,
      meta: link,
      isResizable: true,
      shouldCloseOnEsc: false,
      width: 500,
      height: 500,
    }),
    [link, open]
  )

  const buttonTextOk = t("panels.survey.ok", "ok, let's go!")
  const buttonTextNo = t("panels.survey.no", "no, thanks")

  return (
    <ToolbarWrapper onClose={onClose} color={theme.colors.accent}>
      <DragHandle>
        <Stack p={1} spacing={.5}>
          <Typography variant="body2">{text}</Typography>
          <Stack direction="row" spacing={1}>
            <Button size="small" variant="text" onClick={onOpen}>{buttonTextOk}</Button>
            <Button size="small" variant="text" onClick={onClose}>{buttonTextNo}</Button>
          </Stack>
        </Stack>
      </DragHandle>
    </ToolbarWrapper>
  )
}

export default Survey
