import {css, cx} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {getProcessId} from "../../reducers/selectors/graph"
import {getFeatureSettings} from "../../reducers/selectors/settings"
import {ProcessId} from "../../types"
import {PromptContent} from "../../windowManager"
import {WindowKind} from "../../windowManager/WindowKind"
import CommentInput from "../CommentInput"
import ProcessDialogWarnings from "./ProcessDialogWarnings"

export type ToggleProcessActionModalData = {
  action: (processId: ProcessId, comment: string) => Promise<unknown>,
  displayWarnings?: boolean,
}

export function DeployProcessDialog(props: WindowContentProps<WindowKind, ToggleProcessActionModalData>): JSX.Element {
  // TODO: get rid of meta
  const {meta: {action, displayWarnings}} = props.data
  const processId = useSelector(getProcessId)
  const [comment, setComment] = useState("")
  const [validationError, setValidationError] = useState("")
  const featureSettings = useSelector(getFeatureSettings)
  const deploymentCommentSettings = featureSettings.deploymentCommentSettings

  const dispatch = useDispatch()

  const confirmAction = useCallback(
    async () => {
      try {
        await action(processId, comment)
        props.close()
      } catch(error) {
        setValidationError(error?.response?.data)
      }
    },
    [action, comment, dispatch, processId, props],
  )

  const {t} = useTranslation()
  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {title: t("dialog.button.cancel", "Cancel"), action: () => props.close()},
      {title: t("dialog.button.ok", "Ok"), action: () => confirmAction()},
    ],
    [confirmAction, props, t],
  )

  return (
    <PromptContent {...props} buttons={buttons}>
      <div className={cx("modalContentDark")}>
        <h3>{props.data.title}</h3>
        {displayWarnings && <ProcessDialogWarnings/>}
        <CommentInput
          onChange={e => setComment(e.target.value)}
          value={comment}
          defaultValue={deploymentCommentSettings?.exampleComment}
          className={cx(css({
            minWidth: 600,
            minHeight: 80,
          }))}
          autoFocus
        />
          <span className="validation-label-error" title={validationError}>
            {validationError}
          </span>
      </div>
    </PromptContent>
  )
}

export default DeployProcessDialog
