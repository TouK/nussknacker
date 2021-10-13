import {css, cx} from "@emotion/css"
import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {getLoggedUser} from "../../reducers/selectors/settings"
import {useWindows} from "../../windowManager"
import {WindowKind} from "../../windowManager/WindowKind"
import {ThemedButton} from "../themed/ThemedButton"

type Props = {
  onClick: () => void,
  className?: string,
  title: string,
}

function AddButton(props: Props) {
  const {onClick, className, title} = props
  const loggedUser = useSelector(getLoggedUser)

  return loggedUser.isWriter() ?
    (
      <ThemedButton
        className={className}
        onClick={onClick}
        title={title}
      >
        <span className={css({textTransform: "uppercase"})}>
          {title}
        </span>
      </ThemedButton>
    ) :
    null

}

export function AddProcessButton(props: {isSubprocess: boolean, className?: string}): JSX.Element {
  const {isSubprocess} = props
  const {t} = useTranslation()

  const message = isSubprocess ?
    t("addProcessButton.subprocess", "Create new fragment") :
    t("addProcessButton.process", "Create new scenario")

  const {open} = useWindows()
  const onClick = () => open<{test: number}>({
    isResizable: true,
    isModal: true,
    shouldCloseOnEsc: true,
    kind: isSubprocess ? WindowKind.addSubProcess : WindowKind.addProcess,
    title: message,
  })

  return (
    <AddButton className={cx(props.className)} onClick={onClick} title={message}/>
  )
}
