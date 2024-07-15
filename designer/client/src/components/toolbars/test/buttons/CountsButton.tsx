import { Box, Fade, LinearProgress } from "@mui/material";
import React, { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { useIntervalWhen } from "rooks";
import Icon from "../../../../assets/img/toolbarButtons/counts.svg";
import { getProcessCountsRefresh, isFragment } from "../../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ButtonsVariant, ToolbarButton, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";

// TODO: counts and metrics should not be visible in archived process
function CountsButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const featuresSettings = useSelector(getFeatureSettings);
    const refresh = useSelector(getProcessCountsRefresh);
    const fragment = useSelector(isFragment);
    const { open } = useWindows();
    const { disabled, type } = props;

    const [percent, setPercent] = useState(0);
    const enabled = refresh && refresh.last + refresh.nextIn > Date.now();
    useIntervalWhen(
        () => {
            setPercent(Math.round(((refresh.last + refresh.nextIn - Date.now()) / refresh.nextIn) * 100));
        },
        200,
        enabled,
    );
    const { variant } = useContext(ToolbarButtonsContext);

    return featuresSettings?.counts && !fragment ? (
        <Box sx={{ position: "relative" }}>
            <ToolbarButton
                name={t("panels.actions.test-counts.name", "counts")}
                title={t("panels.actions.test-counts.button.title", "count node invocations in given period")}
                icon={<Icon />}
                disabled={disabled}
                onClick={() =>
                    open({
                        kind: WindowKind.calculateCounts,
                    })
                }
                type={type}
            />
            <Fade in={enabled}>
                <LinearProgress
                    variant="determinate"
                    value={percent}
                    sx={{
                        position: "absolute",
                        inset: 0,
                        height: "auto",
                        pointerEvents: "none",
                        margin: variant === ButtonsVariant.small ? 0 : "4px",
                        background: "transparent",
                        mixBlendMode: "luminosity",
                        borderRadius: "6px",
                        "> span": {
                            opacity: 0.25,
                        },
                    }}
                />
            </Fade>
        </Box>
    ) : null;
}

export default CountsButton;
