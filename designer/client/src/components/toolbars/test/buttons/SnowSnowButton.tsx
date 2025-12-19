import { AcUnit, Park, Sledding } from "@mui/icons-material";
import React, { memo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useIntervalWhen } from "rooks";

import { userSettingsToggle } from "../../../../actions/nk/userSettings";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function SnowSnowButton(props: ToolbarButtonProps) {
    const dispatch = useAppDispatch();
    const userSettings = useAppSelector(getUserSettings);
    const { t } = useTranslation();

    const [Icon, setIcon] = useState(() => AcUnit);
    useIntervalWhen(
        () => {
            setIcon(() => {
                const index = Math.round(Math.random() * 2);
                console.log(index);
                return [Sledding, AcUnit, Park][index];
            });
        },
        5000,
        true,
    );

    return (
        <ToolbarButton
            {...props}
            name={t("panels.actions.snow-snow.name", "Let it snow!")}
            icon={<Icon sx={{ width: "auto", padding: "5%" }} />}
            onClick={() => dispatch(userSettingsToggle(["scenario.isItSnowing"]))}
            isActive={userSettings["scenario.isItSnowing"]}
        />
    );
}

export default memo(SnowSnowButton);
