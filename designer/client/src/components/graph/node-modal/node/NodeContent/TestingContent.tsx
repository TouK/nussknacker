import { Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { getUserSettings } from "../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../types/node";
import type { NodeState } from "../useNodeState";
import { InputData } from "./TestingContentElements/InputData";
import { MockResponse } from "./TestingContentElements/mockResponse";

interface Props {
    node: NodeType;
    onChange?: NodeState["onChange"];
}

export const TestingContent = ({ node, onChange }: Props) => {
    const { t } = useTranslation();
    const { getViewForNode } = useTestingContentRenderer();
    const view = getViewForNode(node, onChange);

    return (
        view || <Typography p={2}>{t("testingContent.noSettingsAvailable", "No testing settings available for selected node")}</Typography>
    );
};

export function useTestingContentRenderer() {
    const settings = useAppSelector(getUserSettings);

    const CONFIG = [
        {
            when: (node: NodeType) => node.type === "Source",
            render: (node: NodeType) => <InputData sourceId={node.id} />,
        },
        {
            when: (node: NodeType) =>
                settings["node.showMockFieldOnEnrichers"] && node.type === "Enricher" && node.service.id !== "decision-table",
            render: (node: NodeType, onChange?: NodeState["onChange"]) => <MockResponse node={node} onChange={onChange} />,
        },
    ];

    const getViewForNode = (node: NodeType, onChange?: NodeState["onChange"]) => {
        const matched = CONFIG.find((cfg) => cfg.when(node));
        return matched?.render(node, onChange) ?? null;
    };

    return { getViewForNode };
}
