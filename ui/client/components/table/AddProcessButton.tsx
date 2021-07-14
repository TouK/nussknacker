import cn from "classnames"
import React, {useState} from "react"
import {useSelector} from "react-redux"
import {useClashedNames} from "../../containers/hooks/useClashedNames"
import {getLoggedUser} from "../../reducers/selectors/settings"
import AddProcessDialog from "../AddProcessDialog"
import {ThemedButton} from "../themed/ThemedButton"
import {useTranslation} from "react-i18next"
import {css} from "emotion"

type Props = {
  onClick: () => void,
  className?: string,
  title: string,
}

function AddButton(props: Props) {
  const {onClick, className, title} = props
  const loggedUser = useSelector(getLoggedUser)

  return loggedUser.isWriter() ? (
    <ThemedButton
      className={className}
      onClick={onClick}
      title={title}
    >
      <span className={css({textTransform: "uppercase"})}>
        {title}
      </span>
    </ThemedButton>
  ) : null

}

export function AddProcessButton(props: {isSubprocess: boolean, className?: string}) {
  const {isSubprocess} = props
  const [addOpened, setAddOpened] = useState(false)
  const clashedNames = useClashedNames(addOpened)
  const {t} = useTranslation()

  const message = isSubprocess ? t("addProcessButton.subprocess", "Create new fragment") :
    t("addProcessButton.process", "Create new scenario")

  return (
    <>
      <AddButton className={cn(props.className)} onClick={() => setAddOpened(true)} title={message}/>
      <AddProcessDialog
        onClose={() => setAddOpened(false)}
        isOpen={addOpened}
        isSubprocess={isSubprocess}
        message={message}
        clashedNames={clashedNames}
      />
    </>
  )
}
