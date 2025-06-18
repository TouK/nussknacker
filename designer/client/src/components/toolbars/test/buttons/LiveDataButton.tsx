import { Insights } from "@mui/icons-material";
import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useIntervalWhen } from "rooks";

import { fetchAndDisplayLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import { getLiveDataRefresh, isReadyForLiveData } from "../../../../reducers/selectors/getLiveData";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

// adjusted by eye for a good looking indicator with paused 100%
function adjustProgress(percent: number) {
    return Math.min(percent * 3 + 15, 100);
}

export function LiveDataButton(props: ToolbarButtonProps) {
    const dispatch = useDispatch();
    const { t } = useTranslation();

    const refresh = useSelector(getLiveDataRefresh);
    const readyForLiveData = useSelector(isReadyForLiveData);

    const { disabled, type, title } = props;

    const [percent, setPercent] = useState(0);

    const enabled = useMemo(() => refresh && refresh.last + refresh.nextIn > Date.now(), [refresh]);

    useIntervalWhen(
        () => {
            const percent = Math.round(((refresh.last + refresh.nextIn - Date.now()) / refresh.nextIn) * 100);
            setPercent(adjustProgress(percent));
        },
        200,
        enabled,
    );

    useEffect(() => {
        if (!enabled) {
            setPercent(0);
        }
    }, [enabled]);

    return (
        <ToolbarButton
            isLoading={readyForLiveData && !disabled && refresh?.nextIn > 5000 && percent > 0}
            loadingVariant={"determinate"}
            loadingProgress={percent}
            isActive={enabled}
            name={t("panels.actions.live-data.name", "live data")}
            title={title ?? t("panels.actions.live-data.button.title", "live data")}
            icon={<Insights sx={{ width: "auto", padding: "5%" }} />}
            disabled={!readyForLiveData || disabled}
            onClick={() => {
                if (!enabled) {
                    dispatch(fetchAndDisplayLiveData(1, true));
                } else {
                    dispatch(stopLiveData());
                }
            }}
            type={type}
        />
    );
}
