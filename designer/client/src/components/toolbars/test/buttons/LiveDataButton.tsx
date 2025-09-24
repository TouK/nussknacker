import { Insights } from "@mui/icons-material";
import React, { memo, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useIntervalWhen } from "rooks";

import { Initiator, startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import type { ThunkAction } from "../../../../actions/reduxTypes";
import {
    getIsLiveDataWorking,
    getLiveDataLastUpdate,
    getLiveDataNextUpdate,
    isReadyForLiveData,
} from "../../../../reducers/selectors/getLiveData";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

// adjusted by eye for a good looking indicator with paused 100%
function adjustProgress(percent: number) {
    return Math.min(percent * 3 + 15, 100);
}

const LiveDataButton = memo(function LiveDataButton(props: ToolbarButtonProps) {
    const dispatch = useAppDispatch();
    const { t } = useTranslation();

    const nextIn = useAppSelector(getLiveDataNextUpdate);
    const last = useAppSelector(getLiveDataLastUpdate);
    const working = useAppSelector(getIsLiveDataWorking);
    const readyForLiveData = useAppSelector(isReadyForLiveData);

    const { disabled, type, title } = props;

    const [percent, setPercent] = useState(0);

    useIntervalWhen(
        () => {
            const percent = Math.round(((last + nextIn - Date.now()) / nextIn) * 100);
            setPercent(adjustProgress(percent));
        },
        200,
        working && nextIn > 5000,
    );

    useEffect(() => {
        if (!working) {
            setPercent(0);
        }
    }, [working]);

    return (
        <>
            <ToolbarButton
                isLoading={readyForLiveData && !disabled && nextIn > 5000 && percent > 0}
                loadingVariant={"determinate"}
                loadingProgress={percent}
                isActive={working}
                name={t("panels.actions.live-data.name", "live data")}
                title={title ?? t("panels.actions.live-data.button.title", "live data")}
                icon={<Insights sx={{ width: "auto", padding: "5%" }} />}
                disabled={!readyForLiveData || disabled}
                onClick={() => dispatch(toggleLiveData())}
                type={type}
            />
        </>
    );
});

function toggleLiveData(): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const action = getIsLiveDataWorking(state) ? stopLiveData(Initiator.button) : startLiveData(null, true);
        dispatch(action);
    };
}

export default LiveDataButton;
