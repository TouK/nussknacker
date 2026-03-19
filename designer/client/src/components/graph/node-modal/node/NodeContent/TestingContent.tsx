import { Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { useUserSettings } from "../../../../../common/useUserSettings";
import type { ScenarioGraph } from "../../../../../types/scenarioGraph";
import type { NodeTypeDetailsContentProps } from "../../NodeTypeDetailsContent";
import type { NodeState } from "../useNodeState";
import { Assertions } from "./TestingContentElements/Assertions";
import { InputDataRecords } from "./TestingContentElements/InputDataRecords";
import { MockResponse } from "./TestingContentElements/MockResponse";

export interface TestingContentProps extends Pick<NodeTypeDetailsContentProps, "node" | "edges"> {
    onChange?: NodeState["onChange"];
    scenarioGraph: ScenarioGraph;
}

export const TestingContent = ({ node, edges, onChange, scenarioGraph }: TestingContentProps) => {
    const { t } = useTranslation();
    const { getViewForNode } = useTestingContentRenderer();
    const view = getViewForNode({ node, edges, onChange, scenarioGraph });

    return (
        view || <Typography p={2}>{t("testingContent.noSettingsAvailable", "No testing settings available for selected node")}</Typography>
    );
};

export function useTestingContentRenderer() {
    const [showMockFieldOnEnrichers] = useUserSettings("node.showMockFieldOnEnrichers");

    const CONFIG: { when: (node: TestingContentProps["node"]) => boolean; render: (props: TestingContentProps) => React.JSX.Element }[] = [
        {
            when: (node) => node.type === "Source",
            render: ({ node, edges, scenarioGraph }) => (
                <>
                    <InputDataRecords sourceId={node.id} node={node} scenarioGraph={scenarioGraph} />
                    <Assertions node={node} edges={edges} />
                </>
            ),
        },
        {
            when: (node) => showMockFieldOnEnrichers && node.type === "Enricher" && node.service.id !== "decision-table",
            render: ({ node, edges, scenarioGraph, onChange }) => (
                <>
                    <MockResponse node={node} edges={edges} onChange={onChange} scenarioGraph={scenarioGraph} />
                    <Assertions node={node} edges={edges} />
                </>
            ),
        },
        {
            when: () => true,
            render: ({ node, edges }) => <Assertions node={node} edges={edges} />,
        },
    ];

    const getViewForNode = ({ node, edges, onChange, scenarioGraph }: TestingContentProps) => {
        const matched = CONFIG.find((cfg) => cfg.when(node));
        return matched?.render({ node, edges, onChange, scenarioGraph }) ?? null;
    };

    return { getViewForNode };
}
