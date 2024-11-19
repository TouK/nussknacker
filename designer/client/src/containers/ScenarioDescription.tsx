import { Description } from "@mui/icons-material";
import { IconButton } from "@mui/material";
import React, { useCallback, useEffect, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { getProcessUnsavedNewName, getScenario, getScenarioDescription } from "../reducers/selectors/graph";
import { useWindows, WindowKind } from "../windowManager";
import { WindowType } from "@touk/window-manager";
import { Scenario } from "../components/Process/types";
import NodeUtils from "../components/graph/NodeUtils";
import { NodeOrPropertiesType } from "../types";

export const DescriptionViewMode = {
    descriptionView: "description",
    descriptionEdit: "descriptionEdit",
} as const;

export type DescriptionViewMode = (typeof DescriptionViewMode)[keyof typeof DescriptionViewMode];

export function useOpenDescription() {
    const { open } = useWindows();
    return useCallback(
        (
            node: NodeOrPropertiesType,
            scenario: Scenario,
            descriptionViewMode?: DescriptionViewMode,
            layoutData?: WindowType["layoutData"],
        ) =>
            open({
                kind: descriptionViewMode === DescriptionViewMode.descriptionEdit ? WindowKind.editDescription : WindowKind.viewDescription,
                isResizable: true,
                shouldCloseOnEsc: false,
                meta: { node, scenario },
                layoutData,
            }),
        [open],
    );
}

export const ScenarioDescription = () => {
    const [description, showDescription] = useSelector(getScenarioDescription);
    const scenario = useSelector(getScenario);
    const name = useSelector(getProcessUnsavedNewName);
    const processProperties = useMemo(() => NodeUtils.getProcessProperties(scenario, name), [name, scenario]);

    const openDescription = useOpenDescription();

    const ref = useRef<HTMLButtonElement>();

    const handleOpenDescription = useCallback(() => {
        if (!ref.current) return;
        const { top, left } = ref.current.getBoundingClientRect();
        openDescription(
            processProperties,
            scenario,
            description ? DescriptionViewMode.descriptionView : DescriptionViewMode.descriptionEdit,
            { top, left, width: 600 },
        );
    }, [description, openDescription, processProperties, scenario]);

    useEffect(
        () => {
            if (description && showDescription) {
                // delaying this is a cheap way to wait for stable positions
                setTimeout(handleOpenDescription, 750);
            }
        },
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [],
    );

    const { t } = useTranslation();
    const title = t("graph.description.toggle", "toggle description view");

    if (!description) return null;

    return (
        <IconButton
            ref={ref}
            title={title}
            onClick={handleOpenDescription}
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
