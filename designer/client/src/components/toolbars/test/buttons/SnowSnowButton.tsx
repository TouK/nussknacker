import { AcUnit, Park, Sledding } from "@mui/icons-material";
import React, { memo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useIntervalWhen } from "rooks";

import { userSettingsToggle } from "../../../../actions/nk/userSettings";
import { useUserSettings } from "../../../../common/useUserSettings";
import { SNOW_SNOW_FLAG } from "../../../../containers/SnowSnow";
import { useAppDispatch } from "../../../../store/storeHelpers";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function SnowSnowButton(props: ToolbarButtonProps) {
    const dispatch = useAppDispatch();
    const [isSnowing] = useUserSettings(SNOW_SNOW_FLAG);
    const { t } = useTranslation();

    const [Icon, setIcon] = useState(() => AcUnit);
    useIntervalWhen(
        () => {
            setIcon(() => {
                const index = Math.round(Math.random() * 2);
                return [Sledding, AcUnit, Park][index];
            });
        },
        5000,
        true,
    );

    return (
        <ToolbarButton
            {...props}
            name={t("panels.actions.snow-snow.name", SNOW_SNOW_FLAG)}
            icon={<Icon sx={{ width: "auto", padding: "5%" }} />}
            onClick={() => dispatch(userSettingsToggle([SNOW_SNOW_FLAG]))}
            isActive={isSnowing}
        />
    );
}

export default memo(SnowSnowButton);
