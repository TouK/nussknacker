import { Insights } from "@mui/icons-material";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useIntervalWhen } from "rooks";

import { startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import type { ThunkAction, ThunkDispatch } from "../../../../actions/reduxTypes";
import {
    getIsLiveDataWorking,
    getLiveDataLastUpdate,
    getLiveDataNextUpdate,
    isReadyForLiveData,
} from "../../../../reducers/selectors/getLiveData";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

// adjusted by eye for a good looking indicator with paused 100%
function adjustProgress(percent: number) {
    return Math.min(percent * 3 + 15, 100);
}

export function LiveDataButton(props: ToolbarButtonProps) {
    const dispatch = useDispatch<ThunkDispatch>();
    const { t } = useTranslation();

    const nextIn = useSelector(getLiveDataNextUpdate);
    const last = useSelector(getLiveDataLastUpdate);
    const working = useSelector(getIsLiveDataWorking);
    const readyForLiveData = useSelector(isReadyForLiveData);

    const { disabled, type } = props;

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
        <ToolbarButton
            isLoading={readyForLiveData && !disabled && nextIn > 5000 && percent > 0}
            loadingVariant={"determinate"}
            loadingProgress={percent}
            isActive={working}
            name={t("panels.actions.live-data.name", "live data")}
            title={t("panels.actions.live-data.button.title", "live data")}
            icon={
                <Insights
                    sx={{
                        width: "auto",
                        padding: "5%",
                    }}
                />
            }
            disabled={!readyForLiveData || disabled}
            onClick={() => dispatch(toggleLiveData())}
            type={type}
        />
    );
}

function toggleLiveData(): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const action = getIsLiveDataWorking(state) ? stopLiveData("button") : startLiveData(null, true);
        dispatch(action);
    };
}
