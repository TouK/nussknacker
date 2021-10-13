import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import {css, cx} from "emotion"
import React, {useCallback, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {displayCurrentProcessVersion, displayProcessActivity} from "../../actions/nk"
import {getProcessId} from "../../reducers/selectors/graph"
import {getFeatureSettings} from "../../reducers/selectors/settings"
import {ProcessId} from "../../types"
import {PromptContent} from "../../windowManager"
import {WindowKind} from "../../windowManager/WindowKind"
import CommentInput from "../CommentInput"
import ValidateDeployComment from "../ValidateDeployComment"
import ProcessDialogWarnings from "./ProcessDialogWarnings"
import {useNkTheme} from "../../containers/theme"

export type ToggleProcessActionModalData = {
  action: (processId: ProcessId, comment: string) => Promise<unknown>,
  displayWarnings?: boolean,
}

export function DeployProcessDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): JSX.Element {
  // TODO: get rid of meta
  const {meta: {action, displayWarnings}} = props.data
  const processId = useSelector(getProcessId)
  const [{comment}, setState] = useState({comment: ""})
  const dispatch = useDispatch()

  const featureSettings = useSelector(getFeatureSettings)

  const settings = {
    ...featureSettings?.commentSettings,
    ...featureSettings?.deploySettings,
  }

  const validated = ValidateDeployComment(comment, settings)

  const confirmAction = useCallback(
    async () => {
      const deploymentPath = window.location.pathname
      props.close()
      await action(processId, comment)
      const currentPath = window.location.pathname
      if (currentPath.startsWith(deploymentPath)) {
        dispatch(displayCurrentProcessVersion(processId))
        dispatch(displayProcessActivity(processId))
      }
    },
    [action, comment, dispatch, processId, props],
  )

  const {t} = useTranslation()
  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {title: t("dialog.button.cancel", "Cancel"), action: () => props.close()},
      {title: t("dialog.button.ok", "Ok"), disabled: !validated.isValid, action: () => confirmAction()},
    ],
    [confirmAction, props, t, validated],
  )
  const {theme} = useNkTheme()

  return (
    <PromptContent {...props} buttons={buttons}>
      <div className={cx("modalContentDark")}>
        <h3>{props.data.title}</h3>
        {displayWarnings && <ProcessDialogWarnings/>}
        <CommentInput
          onChange={e => setState({comment: e.target.value})}
          value={comment}
          className={cx(css({
            minWidth: 600,
            minHeight: 80,
          }),
          !validated.isValid && css({
            "&&, &&:focus": {borderColor: theme.colors.error},
            "::placeholder": {color: theme.colors.error},
          }))}
          autoFocus
        />
      </div>
    </PromptContent>
  )
}
