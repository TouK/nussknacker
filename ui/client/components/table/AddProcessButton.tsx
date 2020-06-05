import React, {useState} from "react"
import createProcessIcon from "../../assets/img/create-process.svg"
import {useSelector} from "react-redux"
import {getLoggedUser} from "../../reducers/selectors/settings"
import {useClashedNames} from "../../containers/useClashedNames"
import AddProcessDialog from "../AddProcessDialog"

type Props = { onClick: () => void }

function AddButton(props: Props) {
  const {onClick} = props
  const loggedUser = useSelector(getLoggedUser)

  return loggedUser.isWriter() ? (
    <button
      type={"button"}
      id="process-add-button"
      className="btn big-blue-button input-group "
      onClick={onClick}
      title={"CREATE NEW PROCESS"}
    >
      <span>CREATE NEW PROCESS</span>
      <img id="add-icon" src={createProcessIcon} alt={"add process icon"}/>
    </button>
  ) : null

}

export function AddProcessButton(props: { isSubprocess: boolean }) {
  const {isSubprocess} = props
  const [addOpened, setAddOpened] = useState(false)
  const clashedNames = useClashedNames(addOpened)

  return (
    <>
      <AddButton onClick={() => setAddOpened(true)}/>
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
