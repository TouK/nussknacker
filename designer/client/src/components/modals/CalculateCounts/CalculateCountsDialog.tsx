/* eslint-disable i18next/no-literal-string */
import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import moment from "moment";
import React, { PropsWithChildren, useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { fetchAndDisplayProcessCounts } from "../../../actions/nk";
import { getProcessId } from "../../../reducers/selectors/graph";
import "../../../stylesheets/visualization.styl";
import { WindowContent } from "../../../windowManager";
import { ChangeableValue } from "../../ChangeableValue";
import { CountsRanges } from "./CountsRanges";
import { Picker, PickerInput } from "./Picker";

type State = {
    from: PickerInput;
    to: PickerInput;
};

const initState = (): State => {
    return {
        from: moment().startOf("day"),
        to: moment().endOf("day"),
    };
};

export function CalculateCountsForm({ value, onChange }: ChangeableValue<State>): JSX.Element {
    const { t } = useTranslation();
    const { from, to } = value;

    const setFrom = useCallback((from: PickerInput) => onChange({ ...value, from }), [onChange, value]);
    const setTo = useCallback((to: PickerInput) => onChange({ ...value, to }), [onChange, value]);
    const setRange = useCallback(([from, to]: [PickerInput, PickerInput]) => onChange({ ...value, from, to }), [onChange, value]);

    return (
        <>
            <Picker label={t("calculateCounts.processCountsFrom", "Scenario counts from")} onChange={setFrom} value={from} />
            <Picker label={t("calculateCounts.processCountsTo", "Scenario counts to")} onChange={setTo} value={to} />
            <CountsRanges label={t("calculateCounts.quickRanges", "Quick ranges")} onChange={setRange} />
        </>
    );
}

export function CountsDialog({ children, ...props }: PropsWithChildren<WindowContentProps>): JSX.Element {
    const { t } = useTranslation();
    const [state, setState] = useState(initState);
    const processId = useSelector(getProcessId);
    const dispatch = useDispatch();

    const confirm = useCallback(async () => {
        await dispatch(fetchAndDisplayProcessCounts(processId, moment(state.from), moment(state.to)));
    }, [dispatch, processId, state.from, state.to]);

    const isStateValid = moment(state.from).isValid() && moment(state.to).isValid();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            {
                title: t("dialog.button.cancel", "Cancel"),
                action: () => {
                    props.close();
                },
            },
            {
                title: t("dialog.button.ok", "Ok"),
                disabled: !isStateValid,
                action: async () => {
                    await confirm();
                    props.close();
                },
            },
        ],
        [confirm, isStateValid, props, t],
    );

    return (
        <WindowContent
            buttons={buttons}
            title={t("calculateCounts.title", "counts")}
            classnames={{
                content: cx(
                    "modalContentDark",
                    css({
                        padding: "0 2em 2em",
                        textAlign: "center",
                        p: {
                            marginTop: "30px",
                        },
                    }),
                ),
            }}
            {...props}
        >
            <CalculateCountsForm value={state} onChange={setState} />
        </WindowContent>
    );
}
