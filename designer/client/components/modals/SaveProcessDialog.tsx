import {css, cx} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch} from "react-redux"
import {displayCurrentProcessVersion, displayProcessActivity, loadProcessToolbarsConfiguration} from "../../actions/nk"
import {PromptContent} from "../../windowManager"
import {CommentInput} from "../CommentInput"
import {ThunkAction} from "../../actions/reduxTypes"
import {getProcessToDisplay, getProcessUnsavedNewName, isProcessRenamed} from "../../reducers/selectors/graph"
import HttpService from "../../http/HttpService"
import {clear} from "../../actions/undoRedoActions"
import {useLocation, useNavigate} from "react-router-dom"

export function SaveProcessDialog(props: WindowContentProps): JSX.Element {
  const location = useLocation()
  const navigate = useNavigate()
  const doRenameProcess = useCallback(
    async (processName: string, newProcessName: string) => {
      const isSuccess = await HttpService.changeProcessName(processName, newProcessName)
      if (isSuccess) {
        navigate({
          ...location,
          pathname: window.location.pathname.replace(encodeURIComponent(processName), encodeURIComponent(newProcessName)),
        }, {replace: true})
      }
      return isSuccess
    },
    [location]
  )

  const saveProcess = useCallback(
    (comment: string): ThunkAction => async (dispatch, getState) => {
      const state = getState()
      const processJson = getProcessToDisplay(state)

      // save changes before rename and force same processId everywhere
      await HttpService.saveProcess(processJson.id, processJson, comment)

      const unsavedNewName = getProcessUnsavedNewName(state)
      const isRenamed = isProcessRenamed(state) && await doRenameProcess(processJson.id, unsavedNewName)
      const processId = isRenamed ? unsavedNewName : processJson.id

      await dispatch(clear())
      await dispatch(displayCurrentProcessVersion(processId))
      await dispatch(displayProcessActivity(processId))
      if (isRenamed) {
        await dispatch(loadProcessToolbarsConfiguration(processId))
      }
    },
    [doRenameProcess]
  )

  const [{comment}, setState] = useState({comment: ""})
  const dispatch = useDispatch()

  const confirmAction = useCallback(
    async () => {
      await dispatch(saveProcess(comment))
      props.close()
    },
    [comment, dispatch, props],
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
      <div className={cx("modalContentDark", css({minWidth: 600}))}>
        <h3>{props.data.title}</h3>
        <CommentInput
          onChange={e => setState({comment: e.target.value})}
          value={comment}
          className={css({
            minWidth: 600,
            minHeight: 80,
          })}
          autoFocus
        />
      </div>
    </PromptContent>
  )
}

export default SaveProcessDialog
