import React, {ChangeEvent, useCallback, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {saveProcess} from "../../actions/nk"
import {getProcessId, getProcessNewId, isProcessRenamed} from "../../reducers/selectors/graph"
import "../../stylesheets/visualization.styl"
import CommentInput from "../CommentInput"
import Dialogs from "./Dialogs"
import GenericModalDialog from "./GenericModalDialog"

function SaveProcessDialog(): JSX.Element {
  const {t} = useTranslation()
  const [comment, setComment] = useState("")

  const dispatch = useDispatch()
  const processId = useSelector(getProcessId)
  const newId = useSelector(getProcessNewId)
  const isRenamed = useSelector(isProcessRenamed)

  const confirm = useCallback(
    async () => {await dispatch(saveProcess(comment))},
    [dispatch, comment],
  )

  const onInputChange = useCallback(
    ({target}: ChangeEvent<HTMLTextAreaElement>) => setComment(target.value),
    [],
  )

  const reset = useCallback(() => setComment(""), [])

  return (
    <GenericModalDialog init={reset} confirm={confirm} type={Dialogs.types.saveProcess}>
      <p>{isRenamed ?
        t("saveProcess.renameTitle", "Save process and rename to {{name}}", {name: newId}) :
        t("saveProcess.title", "Save process {{name}}", {name: processId})
      }</p>
      <CommentInput onChange={onInputChange} value={comment}/>
    </GenericModalDialog>
  )
}

export default SaveProcessDialog

