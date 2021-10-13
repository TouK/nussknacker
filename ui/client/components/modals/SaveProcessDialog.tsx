import {css, cx} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch} from "react-redux"
import {saveProcess} from "../../actions/nk"
import {PromptContent} from "../../windowManager"
import {CommentInput} from "../CommentInput"

export function SaveProcessDialog(props: WindowContentProps): JSX.Element {
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

