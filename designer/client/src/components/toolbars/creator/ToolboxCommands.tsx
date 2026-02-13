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
        description: `Returns all available component definitions for creating new nodes in the scenario. Each component has a componentId and node template.`,
        parameters: z.object({}),
        execute: () => {
            return flatComponents;
        },
    });

    useFrontendAiTool({
        toolName: "add_new_node",
        description: `Creates a new node in the scenario graph. The node is added to the graph immediately. Get available componentId values using get_components tool.`,
        parameters: z.object({
            componentId: z.string().describe("Component definition ID from get_components tool."),
            nodeName: z.string().describe("Unique name for the new node. This will be the node's id in the graph."),
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
