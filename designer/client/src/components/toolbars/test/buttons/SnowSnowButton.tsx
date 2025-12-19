import { AcUnit, Park, Sledding } from "@mui/icons-material";
import React, { memo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useIntervalWhen } from "rooks";

import { useUserSettings } from "../../../../common/userSettings";
import { SNOW_SNOW_FLAG } from "../../../../containers/SnowSnow";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

function SnowSnowButton(props: ToolbarButtonProps) {
    const [userSettings, toggleUserSettings] = useUserSettings();
    const { t } = useTranslation();

    const [Icon, setIcon] = useState(() => AcUnit);
    const icons = [Sledding, AcUnit, Park];

    useIntervalWhen(
        () => {
            setIcon((prev) => {
                const currentIndex = icons.indexOf(prev);
                const nextIndex = (currentIndex + 1) % icons.length;
                return icons[nextIndex];
            });
        },
        3000,
        true,
    );

    return (
        <ToolbarButton
            {...props}
            name={t("panels.actions.snow-snow.name", SNOW_SNOW_FLAG)}
            icon={<Icon sx={{ width: "auto", padding: "5%" }} />}
            onClick={() => toggleUserSettings(["scenario.isItSnowing"])}
            isActive={userSettings["scenario.isItSnowing"]}
        />
    );
}

export default memo(SnowSnowButton);
