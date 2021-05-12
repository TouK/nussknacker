/* eslint-disable i18next/no-literal-string */
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import moment from "moment"
import React, {PropsWithChildren, useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {fetchAndDisplayProcessCounts} from "../../../actions/nk"
import {getProcessId} from "../../../reducers/selectors/graph"
import "../../../stylesheets/visualization.styl"
import {WindowContent} from "../../../windowManager"
import {ChangeableValue} from "../../ChangeableValue"
import {CountsRanges} from "./CountsRanges"
import {Picker, PickerInput} from "./Picker"

type State = {
  from: PickerInput,
  to: PickerInput,
}

const initState = (): State => {
  return {
    from: moment().startOf("day"),
    to: moment().endOf("day"),
  }
}

export function CalculateCountsForm(props: ChangeableValue<State>): JSX.Element {
  const {t} = useTranslation()
  const [state, setState] = useState(props.value)
  const {from, to} = state

  const setFrom = useCallback((from: PickerInput) => {
    setState(current => ({...current, from}))
  }, [setState])

  const setTo = useCallback((to: PickerInput) => {
    setState(current => ({...current, to}))
  }, [setState])

  const setRange = useCallback((value: [PickerInput, PickerInput]) => {
    const [from, to] = value
    setState(current => ({...current, from, to}))
  }, [setState])

  useEffect(
    () => props.onChange(state),
    [props, state],
  )

  const isValid = (input: PickerInput) => moment(input).isValid()
  return (
    <>
      <Picker
        label={t("calculateCounts.processCountsFrom", "Scenario counts from")}
        onChange={setFrom}
        value={from}
      />
      <Picker
        label={t("calculateCounts.processCountsTo", "Scenario counts to")}
        onChange={setTo}
        value={to}
      />
      <CountsRanges
        label={t("calculateCounts.quickRanges", "Quick ranges")}
        onChange={setRange}
      />
    </>
  )
}

export function CountsDialog({children, ...props}: PropsWithChildren<WindowContentProps>): JSX.Element {
  const {t} = useTranslation()
  const [state, setState] = useState(initState)
  const processId = useSelector(getProcessId)
  const dispatch = useDispatch()

  const confirm = useCallback(async () => {
    await dispatch(fetchAndDisplayProcessCounts(processId, moment(state.from), moment(state.to)))
  }, [dispatch, processId, state])

  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {
        title: t("dialog.button.cancel", "Cancel"),
        action: () => {
          props.close()
        },
      },
      {
        title: t("dialog.button.ok", "Ok"),
        action: async () => {
          await confirm()
          props.close()
        },
      },
    ],
    [confirm, props, t],
  )

  return (
    <WindowContent
      buttons={buttons}
      title={t("calculateCounts.title", "counts")}
      classnames={{
        content: "modalContentDark confirmationModal",
      }}
      {...props}
    >
      <CalculateCountsForm value={state} onChange={setState}/>
    </WindowContent>
  )
}
