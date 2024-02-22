/* eslint-disable i18next/no-literal-string */
import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { PropsWithChildren, useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { fetchAndDisplayProcessCounts } from "../../../actions/nk";
import { getProcessName, getScenarioGraph } from "../../../reducers/selectors/graph";
import { WindowContent } from "../../../windowManager";
import { PickerInput } from "./Picker";
import { CalculateCountsForm } from "./CalculateCountsForm";
import moment from "moment";

export type State = {
    from: PickerInput;
    to: PickerInput;
};

const initState = (): State => {
    return {
        from: moment().startOf("day"),
        to: moment().endOf("day"),
    };
};

export function CountsDialog({ children, ...props }: PropsWithChildren<WindowContentProps>): JSX.Element {
    const { t } = useTranslation();
    const [state, setState] = useState(initState);
    const processName = useSelector(getProcessName);
    const dispatch = useDispatch();
    const scenarioGraph = useSelector(getScenarioGraph);

    const confirm = useCallback(async () => {
        await dispatch(fetchAndDisplayProcessCounts(processName, moment(state.from), moment(state.to), scenarioGraph));
    }, [dispatch, processName, state.from, state.to, scenarioGraph]);

    const isStateValid = moment(state.from).isValid() && moment(state.to).isValid();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            {
                title: t("dialog.button.cancel", "Cancel"),
                action: () => {
                    props.close();
                },
                classname: "secondary-button",
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
