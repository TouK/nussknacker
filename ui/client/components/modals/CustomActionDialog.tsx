import React, {useCallback, useState} from "react"
import {useDispatch, useSelector} from "react-redux"
import {getProcessId} from "../../reducers/selectors/graph"
import {loadProcessState} from "../../actions/nk"
import GenericModalDialog from "./GenericModalDialog"
import Dialogs from "./Dialogs"
import HttpService from "../../http/HttpService"
import {getModalDialog} from "../../reducers/selectors/ui"
import {editors} from "../graph/node-modal/editors/expression/Editor"
import {ExpressionLang} from "../graph/node-modal/editors/expression/types"

function CustomActionDialog(): JSX.Element {

  const processId = useSelector(getProcessId)
  const dispatch = useDispatch()
  const action = useSelector(getModalDialog).customAction

  const empty = (action?.parameters || []).reduce((obj, param) => ({...obj, [param.name]: ""}), {})

  const [mapState, setState] = useState(empty)

  //initial state, we want to compute it only once
  const init = useCallback(() => setState({}), [])

  const confirm = useCallback(async () => {
    await HttpService
      .customAction(processId, action.name, mapState)
      .finally(() => dispatch(loadProcessState(processId)))
  }, [processId, action, mapState])

  const setParam = (name: string) => (value: any) =>  setState(current => ({...current, [name]: value}))

  return (
    <GenericModalDialog
      confirm={confirm}
      type={Dialogs.types.customAction}
      init={init}
      header={action?.name}
    >
      <div className="node-table">
        {
          (action?.parameters || []).map(param => {
            const editorType = param.editor.type
            const Editor = editors[editorType]
            const fieldName = param.name
            return (
              <div className={"node-row"} key={param.name}>
                <div className="node-label" title={fieldName}>{fieldName}:</div>
                <Editor
                  editorConfig={param?.editor}
                  className={"node-value"}
                  validators={[]}
                  formatter={null}
                  expressionInfo={null}
                  onValueChange={setParam(fieldName)}
                  expressionObj={{language: ExpressionLang.String, expression: mapState[fieldName]}}
                  values={[]}
                  readOnly={false}
                  key={fieldName}
                  showSwitch={false}
                  showValidation={false}
                  variableTypes={{}}
                />
              </div>
            )
          })
        }
      </div>

    </GenericModalDialog>
  )
}

export default CustomActionDialog
