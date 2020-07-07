import cn from "classnames"
import React, {useState} from "react"
import {useSelector} from "react-redux"
import appStyles from "../../app.styl"
import {useClashedNames} from "../../containers/hooks/useClashedNames"
import {getLoggedUser} from "../../reducers/selectors/settings"
import AddProcessDialog from "../AddProcessDialog"
import {ButtonWithFocus} from "../withFocus"

type Props = {
  onClick: () => void,
  className?: string,
}

function AddButton(props: Props) {
  const {onClick, className} = props
  const loggedUser = useSelector(getLoggedUser)
  const title = "CREATE NEW PROCESS"

  return loggedUser.isWriter() ? (
    <ButtonWithFocus
      type={"button"}
      className={cn(appStyles.btn, appStyles.btnPrimary, className)}
      onClick={onClick}
      title={title}
    >
      <span>{title}</span>
    </ButtonWithFocus>
  ) : null

}

export function AddProcessButton(props: {isSubprocess: boolean, className?: string}) {
  const {isSubprocess} = props
  const [addOpened, setAddOpened] = useState(false)
  const clashedNames = useClashedNames(addOpened)

  return (
    <>
      <AddButton className={cn(props.className)} onClick={() => setAddOpened(true)}/>
      <AddProcessDialog
        onClose={() => setAddOpened(false)}
        isOpen={addOpened}
        isSubprocess={isSubprocess}
        message={isSubprocess ? "Create new subprocess" : "Create new process"}
        clashedNames={clashedNames}
      />
    </>
  )
}
