import { Box } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import moment from "moment";
import type { PropsWithChildren } from "react";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { fetchAndDisplayProcessCounts } from "../../../actions/nk/displayProcessCounts";
import Icon from "../../../assets/img/toolbarButtons/counts.svg";
import { getProcessName } from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { WindowContent } from "../../../windowManager/WindowContent";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { CalculateCountsForm } from "./CalculateCountsForm";
import type { PickerInput } from "./Picker";

export type CountsState = {
    from: PickerInput;
    to: PickerInput;
    refresh?: number | null;
};

const initState = (): CountsState => {
    return {
        from: moment().startOf("day"),
        to: moment().endOf("day"),
    };
};

function CountsDialog({ children, ...props }: PropsWithChildren<WindowContentProps>): React.JSX.Element {
    const { t } = useTranslation();
    const [state, setState] = useState(initState);
    const processName = useAppSelector(getProcessName);
    const dispatch = useAppDispatch();

    const from = useMemo(() => moment(state?.from), [state?.from]);
    const to = useMemo(() => moment(state?.to), [state?.to]);
    const confirm = useCallback(() => {
        return dispatch(
            fetchAndDisplayProcessCounts({
                processName,
                from,
                to,
                refreshIn: state?.refresh,
            }),
        );
    }, [dispatch, from, processName, state?.refresh, to]);

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
            {...props}
        >
            <Box
                sx={{
                    paddingX: "2em",
                    paddingTop: 2,
                    paddingBottom: 4,
                    textAlign: "center",
                }}
            >
                <CalculateCountsForm value={state} onChange={setState} />
            </Box>
        </WindowContent>
    );
}

export default CountsDialog;
