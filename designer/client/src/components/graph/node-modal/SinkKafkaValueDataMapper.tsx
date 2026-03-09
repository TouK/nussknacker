import { Box, Dialog, DialogContent } from "@mui/material";
import React, { useCallback, useMemo, useState } from "react";

import HttpService from "../../../http/HttpService/instance";
import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { TopicEntry } from "../../dataMapper/DataMapper";
import { DataMapper } from "../../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../../dataMapper/DataMapperDialogTitle";
import { EditorType, ExpressionLang } from "./editors/expression/types";
import { StyledLoadingButton } from "./node-action-buttons/StyledLoadingButton";
import { getFindAvailableVariables, getProcessName, getProcessProperties } from "./NodeDetailsContent/selectors";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface Props {
    node: NodeType;
    parameterDefinitions: UIParameter[];
    valuePath: string;
    setProperty: SetProperty;
}

export function SinkKafkaValueDataMapper({ node, parameterDefinitions, valuePath, setProperty }: Props): React.JSX.Element {
    const [open, setOpen] = useState(false);
    const processName = useAppSelector(getProcessName);
    const processProperties = useAppSelector(getProcessProperties);
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables(node.id), [findAvailableVariables, node.id]);
    const [topicEntries, setTopicEntries] = useState<TopicEntry[] | null>(null);

    const fetchTopicDefinitions = useCallback(async (): Promise<TopicEntry[]> => {
        if (topicEntries !== null) return topicEntries;

        const topicParamDef = parameterDefinitions.find((p) => p.name === "Topic");
        const fixedEditor = topicParamDef?.editors?.find((e) => e.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR) as
            | { type: typeof EditorType.FIXED_VALUES_PARAMETER_EDITOR; possibleValues: { expression: string; label: string }[] }
            | undefined;

        const topics = (fixedEditor?.possibleValues ?? []).filter((pv) => pv.label && pv.expression && pv.expression !== "''");

        if (topics.length === 0) return [];

        const results = await Promise.allSettled(
            topics.map(async ({ expression, label }) => {
                const nodeWithTopic = {
                    ...node,
                    ref: {
                        ...node.ref,
                        parameters: (node.ref as { parameters: Array<{ name: string; expression: unknown }> }).parameters.map((p) =>
                            p.name === "Topic" ? { ...p, expression: { language: ExpressionLang.SpEL, expression } } : p,
                        ),
                    },
                } as NodeType;

                const data = await HttpService.validateNode(processName, {
                    nodeData: nodeWithTopic,
                    variableTypes: {},
                    branchVariableTypes: {},
                    outgoingEdges: [],
                    testCases: {},
                    processProperties,
                });

                if (!data) return null;
                const valueParam = data.parameters?.find((p) => p.name === "Value");
                const defaultExpr = valueParam?.defaultValue?.expression;
                if (!defaultExpr) return null;

                let schema: Record<string, unknown>;
                try {
                    const parsed = JSON.parse(defaultExpr);
                    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return null;
                    schema = parsed as Record<string, unknown>;
                } catch {
                    return null;
                }

                if (Object.keys(schema).length === 0) return null;
                return { topic: label, schema };
            }),
        );

        const entries = results
            .filter((r): r is PromiseFulfilledResult<TopicEntry> => r.status === "fulfilled" && r.value !== null)
            .map((r) => r.value);

        setTopicEntries(entries);
        return entries;
    }, [node, parameterDefinitions, processName, processProperties, topicEntries]);

    const handleInsert = useCallback(
        (spel: string) => {
            setProperty(valuePath, { expression: spel, language: ExpressionLang.SpEL });
            setOpen(false);
        },
        [setProperty, valuePath],
    );

    return (
        <>
            <Box display="flex" flexDirection="column" alignItems="flex-end" width="100%">
                <StyledLoadingButton title="Data Mapper" action={() => setOpen(true)} />
            </Box>
            {open && (
                <Dialog open onClose={() => setOpen(false)} maxWidth="xl" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={() => setOpen(false)} />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <DataMapper
                            onInsert={handleInsert}
                            fetchTopicDefinitions={fetchTopicDefinitions}
                            variableTypes={variableTypes}
                            initialExpression={
                                (
                                    node.ref as {
                                        parameters: Array<{ name: string; expression: { language: string; expression: string } }>;
                                    }
                                ).parameters.find((p) => p.name === "Value")?.expression?.expression
                            }
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
