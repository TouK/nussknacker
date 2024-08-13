import { Description } from "@mui/icons-material";
import { IconButton } from "@mui/material";
import React, { useCallback, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { useOpenProperties } from "../components/toolbars/scenarioActions/buttons/PropertiesButton";
import { getScenarioDescription } from "../reducers/selectors/graph";
import { NodeViewMode } from "../windowManager/useWindows";

export const ScenarioDescription = () => {
    const [description, showDescription] = useSelector(getScenarioDescription);

    const openProperties = useOpenProperties();

    const ref = useRef<HTMLButtonElement>();

    const openDescription = useCallback(() => {
        const { top, left } = ref.current.getBoundingClientRect();
        openProperties(NodeViewMode.description, { top, left, width: 600 });
    }, [openProperties]);

    useEffect(() => {
        if (showDescription) {
            openDescription();
        }
    }, []);

    const { t } = useTranslation();
    const title = t("graph.description.toggle", "toggle description view");

    return (
        <IconButton
            ref={ref}
            title={title}
            onClick={openDescription}
            sx={{
                borderRadius: 0,
            }}
            disableFocusRipple
            color="inherit"
        >
            <Description />
        </IconButton>
    );
};
