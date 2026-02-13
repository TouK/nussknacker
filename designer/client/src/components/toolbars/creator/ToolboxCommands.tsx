import { Typography } from "@mui/material";
import { cloneDeep } from "lodash";
import React, { useCallback, useMemo } from "react";
import { z } from "zod";

import { useFilteredComponentGroups } from "../../../reducers/selectors/getFilteredComponentGroups";
import { useAppDispatch } from "../../../store/storeHelpers";
import type { Component } from "../../../types/component";
import type { NodeType } from "../../../types/node";
import { DefaultToolComponent } from "../../aiAssistant/components/DefaultToolComponent";
import { rejectToolCall, useFrontendAiTool } from "../../aiAssistant/useFrontendAiTool";
import { useRegisterCommands } from "../../CommandBar/useRegisterCommands";
import { ComponentIcon } from "./ComponentIcon";

export function ToolboxCommands({ onSelect }: { onSelect: (item: NodeType) => void }) {
    const groups = useFilteredComponentGroups();

    const flatComponents = useMemo(
        () => groups.flatMap(({ components, name }) => components.map((component) => ({ ...component, group: name }))),
        [groups],
    );

    const addToGraph = useCallback(
        ({ label, node }: Component) => {
            onSelect({ ...cloneDeep(node), id: label });
        },
        [onSelect],
    );

    useRegisterCommands(() => {
        const root = {
            id: "component",
            section: "scenario",
            name: "Components",
        };
        return [
            root,
            ...groups.flatMap((group) => {
                if (!group.components.length) return [];
                const parent = {
                    id: [root.id, group.name].join("/"),
                    parent: root.id,
                    name: group.name,
                };
                const children = group.components.map((component) => ({
                    id: [parent.id, component.componentId].join("/"),
                    parent: parent.id,
                    name: component.label,
                    icon: <ComponentIcon node={component.node} />,
                    perform: () => addToGraph(component),
                }));
                return [parent, ...children];
            }),
        ];
    }, [addToGraph, groups]);

    const dispatch = useAppDispatch();

    useFrontendAiTool({
        toolName: "get_components",
        description: `Use this tool to get a list of all available component definitions that can be used to create new nodes in the current scenario. Each component definition includes a template for a node of that type, which you can then use to create a new node.`,
        parameters: z.object({}),
        execute: () => {
            return flatComponents;
        },
    });

    useFrontendAiTool({
        toolName: "add_new_node",
        description: `Use this tool to create a new node (including sticky notes) in the scenario graph. You need to provide a component ID (which can be obtained using another tool that lists components) and a unique name for the new node. After using this tool, you should verify that the node has been added to the graph, for example by using a tool that gets scenario data. IMPORTANT: Adding a node will append it to the end of the 'nodes' array and may change its length, affecting subsequent indexing.`,
        parameters: z.object({
            componentId: z
                .string()
                .describe(
                    "The ID of the component definition to use for creating the node. This can be obtained using another tool that lists components.",
                ),
            nodeName: z
                .string()
                .describe(
                    "A unique name or label for the newly created node. This name will be used to identify the node in the scenario graph.",
                ),
        }),
        render: (props) => (
            <DefaultToolComponent {...props}>
                <Typography>
                    Create new node <strong>{props.args.nodeName}</strong>
                </Typography>
            </DefaultToolComponent>
        ),
        execute: ({ componentId, nodeName }) => {
            const component = flatComponents.find((c) => c.componentId === componentId);
            if (!component) {
                return rejectToolCall("no such component definition");
            }

            addToGraph({ ...component, label: nodeName });
        },
    });

    return null;
}
