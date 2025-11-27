import { Description } from "@mui/icons-material";
import { IconButton } from "@mui/material";
import type { WindowType } from "@touk/window-manager";
import React, { useCallback, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

import type { Scenario } from "../components/Process/types";
import { getProperties, getScenario, getScenarioDescription } from "../reducers/selectors/graph";
import { useAppSelector } from "../store/storeHelpers";
import type { NodeOrPropertiesType } from "../types/node";
import { useWindows } from "../windowManager/useWindows";
import { WindowKind } from "../windowManager/WindowKind";

const measureText = (text: string, font: string, elementWidth: number): { width: number; height: number } => {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    if (!context) return { width: 0, height: 0 };

    context.font = font;
    const lines = text.split("\n");
    let totalHeight = 0;
    let maxWidth = 0;

    lines.forEach((line) => {
        const metrics = context.measureText(line);
        const width = metrics.width;
        const lineHeight = metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;

        // Calculate the number of lines based on the element width
        const lineCount = Math.ceil(width / elementWidth);
        totalHeight += lineHeight * lineCount;
        maxWidth = Math.max(maxWidth, width);
    });

    return { width: Math.min(maxWidth, elementWidth), height: totalHeight };
};

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
        ) => {
            const textMetric = measureText(scenario.scenarioGraph.properties.additionalFields.description, "14px monospace", 600);

            const descriptionHeightInPercentage = (textMetric.height / window.innerHeight) * 100;
            const maxHeightInPercentage = 35;
            const maxPercentageHeightAsNumber = window.innerHeight * (maxHeightInPercentage / 100);
            const isRestrictedMaxHeight = descriptionHeightInPercentage > maxHeightInPercentage;

            return open({
                kind: descriptionViewMode === DescriptionViewMode.descriptionEdit ? WindowKind.editDescription : WindowKind.viewDescription,
                isResizable: true,
                shouldCloseOnEsc: false,
                meta: { node, scenario },
                height: isRestrictedMaxHeight ? maxPercentageHeightAsNumber : undefined,
                layoutData: {
                    ...layoutData,
                },
            });
        },

        [open],
    );
}

const waitForStablePosition = (ref: React.RefObject<HTMLElement>, timeout = 100) => {
    return function wait(callback: (box: DOMRect) => void) {
        const box = ref.current?.getBoundingClientRect();
        setTimeout(() => {
            const currentBox = ref.current?.getBoundingClientRect();
            if (box && currentBox && currentBox.x === box.x && currentBox.y === box.y) return callback(currentBox);
            wait(callback);
        }, timeout);
    };
};

export const ScenarioDescription = () => {
    const [description, showDescription] = useAppSelector(getScenarioDescription);
    const scenario = useAppSelector(getScenario);
    const processProperties = useAppSelector(getProperties);

    const openDescription = useOpenDescription();

    const ref = useRef<HTMLButtonElement>(null);

    const handleOpenDescription = useCallback(() => {
        const wait = waitForStablePosition(ref, 250);
        wait(({ top, left }) =>
            openDescription(
                processProperties,
                scenario,
                description ? DescriptionViewMode.descriptionView : DescriptionViewMode.descriptionEdit,
                { top, left, width: 600 },
            ),
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
