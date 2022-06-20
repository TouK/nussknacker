import ace from "ace-builds/src-noconflict/ace"
import {isEmpty, map, overSome} from "lodash"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {connect} from "react-redux"
import ActionsUtils from "../../../../../actions/ActionsUtils"
import {getProcessDefinitionData} from "../../../../../reducers/selectors/settings"
import {ProcessDefinitionData} from "../../../../../types"
import {getProcessToDisplay} from "../../../../../reducers/selectors/graph"
import ExpressionSuggester from "./ExpressionSuggester"
import HttpService from "../../../../../http/HttpService"
import ProcessUtils from "../../../../../common/ProcessUtils"
import ReactDOMServer from "react-dom/server"
import cn from "classnames"
import {allValid} from "../Validators"
import AceEditor from "./AceWithSettings"
import ValidationLabels from "../../../../modals/ValidationLabels"

export const {TokenIterator} = ace.require("ace/token_iterator")

//to reconsider
// - respect categories for global variables?
// - maybe ESC should be allowed to hide suggestions but leave modal open?

export const identifierRegexpsWithoutDot = [/[#a-zA-Z0-9-_]/]
export const identifierRegexpsIncludingDot = [/[#a-zA-Z0-9-_.]/]

export function isSqlTokenAllowed(iterator, modeId): boolean {
  if (modeId === "ace/mode/sql") {
    let token = iterator.getCurrentToken()
    while (token && (token.type !== "spel.start" && token.type !== "spel.end")) {
      token = iterator.stepBackward()
    }
    return token?.type === "spel.start"
  }
  return false
}

export function isSpelTokenAllowed(iterator, modeId): boolean {
  // We need to handle #dict['Label'], where Label is a string token
  return modeId === "ace/mode/spel"
}

export interface Props extends StateProps {
  inputProps: {
    value: string,
    language: string,
    readOnly?: boolean,
    rows?: number,
    [k: string]: any,
  },
  validators: any[],
  validationLabelInfo: string,
  showValidation?: boolean,
  isMarked?: boolean,
  variableTypes: Record<string, any>,
}

class CustomAceEditorCompleter {
  isTokenAllowed = overSome([isSqlTokenAllowed, isSpelTokenAllowed])
  // We adds hash to identifier pattern to start suggestions just after hash is typed
  identifierRegexps = identifierRegexpsIncludingDot

  constructor(private expressionSuggester: ExpressionSuggester) {
  }

  getCompletions = (editor, session, caretPosition2d, prefix, callback) => {
    const iterator = new TokenIterator(session, caretPosition2d.row, caretPosition2d.column)
    if (!this.isTokenAllowed(iterator, session.$modeId)) {
      callback()
    }

    this.expressionSuggester.suggestionsFor(prefix, caretPosition2d).then(suggestions => {
      // This trick enforce autocompletion to invoke getCompletions even if some result found before - in case if list of suggestions will change during typing
      editor.completer.activated = false
      // We have dot in identifier pattern to enable live autocompletion after dots, but also we remove it from pattern just before callback, because
      // otherwise our results lists will be filtered out (because entries not matches '#full.property.path' but only 'path')
      this.identifierRegexps = identifierRegexpsWithoutDot
      try {
        callback(null, map(suggestions, (s) => {
          const methodName = s.methodName
          const returnType = ProcessUtils.humanReadableType(s.refClazz)
          return {
            name: methodName,
            value: methodName,
            score: s.fromClass ? 1 : 1000,
            meta: returnType,
            description: s.description,
            parameters: s.parameters,
            returnType: returnType,
            className: `${s.fromClass ? `class` : `default`}Method ace_`,
            // force ignore ace internal exactMatch setting based on penalty
            get exactMatch() {
              return 0
            },
            set exactMatch(value) {
              return
            },
          }
        }))
      } finally {
        this.identifierRegexps = identifierRegexpsIncludingDot
      }
    })
  }

  getDocTooltip = (item) => {
    if (item.description || !isEmpty(item.parameters)) {
      const paramsSignature = item.parameters.map(p => `${ProcessUtils.humanReadableType(p.refClazz)} ${p.name}`).join(", ")
      const javaStyleSignature = `${item.returnType} ${item.name}(${paramsSignature})`
      item.docHTML = ReactDOMServer.renderToStaticMarkup((
        <div className="function-docs">
          <b>{javaStyleSignature}</b>
          <hr/>
          <p>{item.description}</p>
        </div>
      ))
    }
  }
}

export const ExpressionSuggest = (props: Props): JSX.Element => {
  const {
    dataResolved,
    isMarked,
    showValidation,
    inputProps,
    validators,
    processingType,
    typesInformation,
    variableTypes,
    validationLabelInfo,
  } = props

  const {value, onValueChange} = inputProps
  const [state, setState] = useState(value)
  const [editorFocused, setEditorFocused] = useState(false)

  const expressionSuggester = useMemo(() => {
    return new ExpressionSuggester(typesInformation, variableTypes, processingType, HttpService)
  }, [processingType, typesInformation, variableTypes])

  useEffect(() => {
    onValueChange(state)
  }, [onValueChange, state])

  const customAceEditorCompleter = useMemo(() => new CustomAceEditorCompleter(expressionSuggester), [expressionSuggester])

  const onChange = useCallback((value: string) => setState(value), [])
  const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), [])

  return dataResolved ?
    (
      <>
        <div className={cn([
          "row-ace-editor",
          showValidation && !allValid(validators, [state]) && "node-input-with-error",
          isMarked && "marked",
          editorFocused && "focused",
          inputProps.readOnly && "read-only",
        ])}
        >
          <AceEditor
            ref={inputProps.ref}
            value={state}
            onChange={onChange}
            onFocus={editorFocus(true)}
            onBlur={editorFocus(false)}
            inputProps={inputProps}
            customAceEditorCompleter={customAceEditorCompleter}
          />
        </div>
        {showValidation && (
          <ValidationLabels
            validators={validators}
            values={[state]}
            validationLabelInfo={validationLabelInfo}
          />
        )}
      </>
    ) :
    null
}

function mapState(state) {
  const definitionData: ProcessDefinitionData = getProcessDefinitionData(state)
  const dataResolved = !isEmpty(definitionData)
  const processDefinitionData: ProcessDefinitionData = dataResolved ? definitionData : {processDefinition: {typesInformation: []}}
  const typesInformation = processDefinitionData.processDefinition.typesInformation
  const processingType = getProcessToDisplay(state).processingType
  return {typesInformation, dataResolved, processingType}
}

export type StateProps = ReturnType<typeof mapState>

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ExpressionSuggest)
