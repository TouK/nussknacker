import {css, cx} from "@emotion/css"
import React, {useCallback, useMemo} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {getLoggedUser} from "../../reducers/selectors/settings"
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

export function useAddProcessButtonProps(isSubprocess?: boolean): { action: () => void, title: string } {
  const {t} = useTranslation()

  const title = useMemo(
    () => isSubprocess ?
      t("addProcessButton.subprocess", "Create new fragment") :
      t("addProcessButton.process", "Create new scenario"),
    [isSubprocess, t]
  )

  const action = useCallback(() => console.log({
    kind: isSubprocess ? "WindowKind.addSubProcess" : "WindowKind.addProcess",
    title,
  }), [isSubprocess, title])

  return useMemo(() => ({title, action}), [action, title])
}

export function AddProcessButton(props: { isSubprocess?: boolean, className?: string }): JSX.Element {
  const {isSubprocess} = props
  const {title, action} = useAddProcessButtonProps(isSubprocess)

  return (
    <AddButton className={cx(props.className)} onClick={action} title={title}/>
  )
}
