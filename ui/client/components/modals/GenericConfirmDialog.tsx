import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import {css, cx} from "emotion"
import React, {PropsWithChildren, useMemo} from "react"
import {useTranslation} from "react-i18next"
import {ConfirmDialogData} from "../../actions/nk"
import {PromptContent} from "../../windowManager"
import {WindowKind} from "../../windowManager/WindowKind"

export function GenericConfirmDialog({children, ...props}: PropsWithChildren<WindowContentProps<WindowKind, ConfirmDialogData>>): JSX.Element {
  // TODO: get rid of meta
  const {meta} = props.data

  const {t} = useTranslation()
  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {
        title: meta.denyText || t("dialog.button.no", "no"),
        action: () => {
          props.close()
        },
      },
      {
        title: meta.confirmText || t("dialog.button.yes", "yes"),
        action: () => {
          meta.onConfirmCallback()
          props.close()
        },
      },
    ],
    [meta, props, t],
  )

  return (
    <PromptContent {...props} buttons={buttons}>
      <div className={cx("modalContentDark", css({minWidth: 400}))}>
        <h3 className={css({textAlign: "center"})}>
          {props.data.title}
        </h3>
        {children}
      </div>
    </PromptContent>
  )
}
