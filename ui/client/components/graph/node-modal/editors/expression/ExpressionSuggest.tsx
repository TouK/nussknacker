import ace from "ace-builds/src-noconflict/ace"
import {isEmpty, map, overSome} from "lodash"
import React, {ReactElement, useCallback, useMemo, useState} from "react"
import {useSelector} from "react-redux"
import {getProcessDefinitionData} from "../../../../../reducers/selectors/settings"
import {getProcessToDisplay} from "../../../../../reducers/selectors/graph"
import ExpressionSuggester from "./ExpressionSuggester"
import HttpService from "../../../../../http/HttpService"
import ProcessUtils from "../../../../../common/ProcessUtils"
import ReactDOMServer from "react-dom/server"
import cn from "classnames"
import {allValid, Validator} from "../Validators"
import AceEditor from "./AceWithSettings"
import ValidationLabels from "../../../../modals/ValidationLabels"
import ReactAce from "react-ace/lib/ace"
import {ExpressionLang} from "./types"
import {TypingResult} from "../../../../../types"
import type {Ace} from "ace-builds"

const {TokenIterator} = ace.require("ace/token_iterator")

//to reconsider
// - respect categories for global variables?
// - maybe ESC should be allowed to hide suggestions but leave modal open?

const identifierRegexpsWithoutDot = [/[#a-zA-Z0-9-_]/]
const identifierRegexpsIncludingDot = [/[#a-zA-Z0-9-_.]/]

function isSqlTokenAllowed(iterator, modeId): boolean {
  if (modeId === "ace/mode/sql") {
    let token = iterator.getCurrentToken()
    while (token && (token.type !== "spel.start" && token.type !== "spel.end")) {
      token = iterator.stepBackward()
    }
    return token?.type === "spel.start"
  }
  return false
}

function isSpelTokenAllowed(iterator, modeId): boolean {
  // We need to handle #dict['Label'], where Label is a string token
  return modeId === "ace/mode/spel"
}

interface InputProps {
  value: string,
  language: ExpressionLang | string,
  readOnly?: boolean,
  rows?: number,
  onValueChange: (value: string) => void,
  ref: React.Ref<ReactAce>,
  className: string,
  cols: number,
}

interface Props {
  inputProps: InputProps,
  validators: Validator[],
  validationLabelInfo: string,
  showValidation?: boolean,
  isMarked?: boolean,
  variableTypes: Record<string, unknown>,
}

interface Completion<P = unknown> extends Ace.Completion {
  description: ReactElement,
  parameters: P[],
  returnType: string,
  name: string,
  docHTML: string,
}

interface Editor extends Ace.Editor {
  readonly completer: {
    activated: boolean,
  },
}

interface EditSession extends Ace.EditSession {
  readonly $modeId: unknown,
}

interface AceEditorCompleter<P = unknown> extends Ace.Completer {
  getDocTooltip(item: Completion<P>): void,

  getCompletions(
    editor: Editor,
    session: EditSession,
    position: Ace.Point,
    prefix: string,
    callback: Ace.CompleterCallback
  ): void,
}

class CustomAceEditorCompleter implements AceEditorCompleter<{ refClazz: TypingResult, name: string }> {
  private isTokenAllowed = overSome([isSqlTokenAllowed, isSpelTokenAllowed])
  // We adds hash to identifier pattern to start suggestions just after hash is typed
  private identifierRegexps = identifierRegexpsIncludingDot

  constructor(private expressionSuggester: ExpressionSuggester) {
  }

  getCompletions(editor: Editor, session: EditSession, caretPosition2d: Ace.Point, prefix: string, callback: Ace.CompleterCallback): void {
    const iterator = new TokenIterator(session, caretPosition2d.row, caretPosition2d.column)
    if (!this.isTokenAllowed(iterator, session.$modeId)) {
      callback(null, [])
    }

    this.expressionSuggester.suggestionsFor(editor.getValue(), caretPosition2d).then(suggestions => {
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

  getDocTooltip(item: Completion<{ refClazz: TypingResult, name: string }>): void {
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

function ExpressionSuggest(props: Props): JSX.Element {
  const {
    isMarked,
    showValidation,
    inputProps,
    validators,
    variableTypes,
    validationLabelInfo,
  } = props

  const definitionData = useSelector(getProcessDefinitionData)
  const dataResolved = !isEmpty(definitionData)
  const processDefinitionData = dataResolved ? definitionData : {processDefinition: {typesInformation: []}}
  const typesInformation = processDefinitionData.processDefinition.typesInformation
  const {processingType} = useSelector(getProcessToDisplay)

  const {value, onValueChange} = inputProps
  const [editorFocused, setEditorFocused] = useState(false)

  const expressionSuggester = useMemo(() => {
    return new ExpressionSuggester(typesInformation, variableTypes, processingType, HttpService)
  }, [processingType, typesInformation, variableTypes])

  const customAceEditorCompleter = useMemo(() => new CustomAceEditorCompleter(expressionSuggester), [expressionSuggester])

  const onChange = useCallback((value: string) => onValueChange(value), [onValueChange])
  const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), [])

  return dataResolved ?
    (
      <>
        <div className={cn([
          "row-ace-editor",
          showValidation && !allValid(validators, [value]) && "node-input-with-error",
          isMarked && "marked",
          editorFocused && "focused",
          inputProps.readOnly && "read-only",
        ])}
        >
          <AceEditor
            ref={inputProps.ref}
            value={value}
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
            values={[value]}
            validationLabelInfo={validationLabelInfo}
          />
        )}
      </>
    ) :
    null
}

export default ExpressionSuggest
