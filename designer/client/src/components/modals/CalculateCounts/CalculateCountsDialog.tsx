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
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import Icon from "../../../assets/img/toolbarButtons/counts.svg";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";

export type State = {
    from: PickerInput;
    to: PickerInput;
    refresh?: number | null;
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

    const from = useMemo(() => moment(state?.from), [state?.from]);
    const to = useMemo(() => moment(state?.to), [state?.to]);
    const confirm = useCallback(() => {
        return dispatch(
            fetchAndDisplayProcessCounts({
                scenarioGraph,
                processName,
                from,
                to,
                refreshIn: state?.refresh,
            }),
        );
    }, [dispatch, from, processName, scenarioGraph, state?.refresh, to]);

    const isStateValid = from.isValid() && to.isValid() && from.isBefore(to);
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            {
                title: t("dialog.button.cancel", "Cancel"),
                action: () => {
                    props.close();
                },
                classname: LoadingButtonTypes.secondaryButton,
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
            icon={<WindowHeaderIconStyled as={Icon} type={props.data.kind} />}
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
