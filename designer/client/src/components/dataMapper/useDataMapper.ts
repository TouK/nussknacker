import { useCallback, useMemo, useState } from "react";

import HttpService from "../../http/HttpService/instance";
import { useAppSelector } from "../../store/storeHelpers";
import type { VariableTypes } from "../../types/validation";
import { toNullSafe, typingResultToSample } from "../builderComponents/typeUtils";
import { getProcessName, getProcessProperties } from "../graph/node-modal/NodeDetailsContent/selectors";
import type { ContextData, FieldDef, TopicEntry } from "./dataMapperUtils";
import {
    fieldsFromSample,
    genSpelFromFields,
    INITIAL_FIELDS,
    KAFKA_TOPIC_PROBE_NODE,
    makeField,
    makeMapEntry,
    nextId,
    parseSpelToFields,
    SAMPLE_CONTEXT,
} from "./dataMapperUtils";

interface UseDataMapperOptions {
    initialContext?: ContextData;
    initialExpression?: string;
    variableTypes?: VariableTypes;
    isEmbedded: boolean;
    fetchTopicDefinitionsOverride?: () => Promise<TopicEntry[]>;
}

// eslint-disable-next-line @typescript-eslint/explicit-module-boundary-types
export function useDataMapper({
    initialContext,
    initialExpression,
    variableTypes,
    isEmbedded,
    fetchTopicDefinitionsOverride,
}: UseDataMapperOptions) {
    const processName = useAppSelector(getProcessName);
    const processProperties = useAppSelector(getProcessProperties);

    const [context, setContext] = useState<ContextData>(() => initialContext ?? (variableTypes ? {} : SAMPLE_CONTEXT));
    const [fields, setFields] = useState<FieldDef[]>(() => {
        if (initialExpression) {
            const parsed = parseSpelToFields(initialExpression);
            if (parsed) return parsed;
        }
        return isEmbedded ? [] : INITIAL_FIELDS.map((f) => ({ ...f, id: nextId() }));
    });
    const [selField, setSelField] = useState<number | null>(null);
    const [selPath, setSelPath] = useState<string | null>(null);
    const [dragOverId, setDragOverId] = useState<number | null>(null);
    const [showTargetSample, setShowTargetSample] = useState(false);
    const [showContextSample, setShowContextSample] = useState(false);
    const [showTopicPicker, setShowTopicPicker] = useState(false);
    const [topicEntries, setTopicEntries] = useState<TopicEntry[]>([]);
    const [topicsLoading, setTopicsLoading] = useState(false);
    const [contextFilter, setContextFilter] = useState("");
    const [dropZoneActive, setDropZoneActive] = useState(false);

    const enrichedContext = useMemo<ContextData>(() => {
        if (!variableTypes) return context;
        const merged: ContextData = { ...context };
        for (const [key, typingResult] of Object.entries(variableTypes)) {
            const existing = merged[key];
            const elem0 = Array.isArray(existing) ? existing[0] : undefined;
            const lacksStructure =
                existing === undefined ||
                (Array.isArray(existing) && existing.length === 0) ||
                (Array.isArray(existing) &&
                    existing.length > 0 &&
                    (elem0 === null || typeof elem0 !== "object" || Object.keys(elem0 as object).length === 0));
            if (lacksStructure) {
                const sample = typingResultToSample(typingResult);
                if (sample !== null) merged[key] = sample;
            }
        }
        return merged;
    }, [context, variableTypes]);

    const mappedCount = useMemo(() => fields.filter((f) => f.expression || (f.useMapBuilder && f.mapEntries.length > 0)).length, [fields]);
    const spelOutput = useCallback(() => genSpelFromFields(fields), [fields]);

    const addField = useCallback(() => setFields((f) => [...f, makeField()]), []);

    const addFieldFromDrop = useCallback((path: string) => {
        const lastSegment = path.split(".").pop()?.replace(/\?/g, "") ?? "";
        const field = makeField(lastSegment);
        field.expression = path;
        setFields((f) => [...f, field]);
        setDragOverId(null);
        setDropZoneActive(false);
    }, []);

    const removeField = useCallback(
        (id: number) => {
            setFields((f) => f.filter((x) => x.id !== id));
            if (selField === id) setSelField(null);
        },
        [selField],
    );

    const updateField = useCallback((id: number, key: keyof FieldDef, val: unknown) => {
        setFields((f) => f.map((x) => (x.id !== id ? x : { ...x, [key]: val })));
    }, []);

    const moveField = useCallback((id: number, dir: 1 | -1) => {
        setFields((f) => {
            const idx = f.findIndex((x) => x.id === id);
            if (idx < 0) return f;
            const ni = idx + dir;
            if (ni < 0 || ni >= f.length) return f;
            const c = [...f];
            [c[idx], c[ni]] = [c[ni], c[idx]];
            return c;
        });
    }, []);

    const addMapEntry = useCallback((fieldId: number) => {
        setFields((fs) => fs.map((f) => (f.id !== fieldId ? f : { ...f, mapEntries: [...f.mapEntries, makeMapEntry()] })));
    }, []);

    const handleAutoMap = useCallback(() => {
        const pathMap = new Map<string, string>();
        function traverse(obj: unknown, path: string) {
            if (obj !== null && typeof obj === "object" && !Array.isArray(obj)) {
                Object.entries(obj as Record<string, unknown>).forEach(([k, v]) => traverse(v, `${path}.${k}`));
            } else {
                const key = path.split(".").pop()!.toLowerCase().replace(/[_\s]/g, "");
                if (!pathMap.has(key)) pathMap.set(key, path);
            }
        }
        Object.entries(enrichedContext).forEach(([key, val]) => traverse(val, key));
        setFields((prev) =>
            prev.map((f) => {
                if (f.expression || (f.useMapBuilder && f.mapEntries.length > 0)) return f;
                const normalized = f.name.toLowerCase().replace(/[_\s]/g, "");
                const match = pathMap.get(normalized);
                return match ? { ...f, expression: toNullSafe(`#${match}`) } : f;
            }),
        );
    }, [enrichedContext]);

    const onTreeSelect = useCallback(
        (path: string) => {
            setSelPath(path);
            if (selField != null) {
                setFields((f) => f.map((x) => (x.id === selField ? { ...x, expression: toNullSafe(`#${path}`) } : x)));
            }
        },
        [selField],
    );

    const onDrop = useCallback((path: string, fieldId: number) => {
        const lastSegment = path.split(".").pop()?.replace(/\?/g, "") ?? "";
        setFields((prev) =>
            prev.map((x) => {
                if (x.id !== fieldId) return x;
                const nameUpdate = !x.name?.trim() && lastSegment ? { name: lastSegment } : {};
                return { ...x, expression: path, ...nameUpdate };
            }),
        );
        setDragOverId(null);
    }, []);

    const applyTargetSample = useCallback((parsed: unknown, mode: "replace" | "merge"): string | null => {
        const newFields = fieldsFromSample(parsed);
        if (newFields.length === 0) return 'JSON must be a flat object, e.g. {"name": "value"}';
        if (mode === "replace") {
            setFields(newFields.map((f) => ({ ...f, id: nextId() })));
        } else {
            setFields((prev) => {
                const existing = new Set(prev.map((x) => x.name));
                const toAdd = newFields.filter((nf) => !existing.has(nf.name)).map((f) => ({ ...f, id: nextId() }));
                return [...prev, ...toAdd];
            });
        }
        return null;
    }, []);

    const applyContextSample = useCallback((parsed: unknown, _mode: "replace" | "merge"): string | null => {
        if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
            return "Context JSON must be a top-level object where each key is a variable name";
        }
        setContext(parsed as ContextData);
        return null;
    }, []);

    const defaultFetchTopicDefinitions = useCallback(async (): Promise<TopicEntry[]> => {
        if (!processName || !processProperties) return [];
        const probe = await HttpService.validateNode(processName, {
            nodeData: KAFKA_TOPIC_PROBE_NODE as never,
            variableTypes: {},
            branchVariableTypes: {},
            outgoingEdges: [],
            testCases: {},
            processProperties,
        });
        if (!probe) return [];
        const topicParam = probe.parameters?.find((p) => p.name === "Topic");
        const fixedEditor = topicParam?.editors?.find((e) => e.type === "FixedValuesParameterEditor") as
            | { possibleValues: { expression: string; label: string }[] }
            | undefined;
        const topics = (fixedEditor?.possibleValues ?? []).filter((pv) => pv.label && pv.expression && pv.expression !== "''");
        if (topics.length === 0) return [];

        const results = await Promise.allSettled(
            topics.map(async ({ expression, label }) => {
                const nodeWithTopic = {
                    ...KAFKA_TOPIC_PROBE_NODE,
                    ref: { ...KAFKA_TOPIC_PROBE_NODE.ref, parameters: [{ name: "Topic", expression: { language: "spel", expression } }] },
                };
                const data = await HttpService.validateNode(processName, {
                    nodeData: nodeWithTopic as never,
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
                try {
                    const parsed = JSON.parse(defaultExpr);
                    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed) || Object.keys(parsed).length === 0)
                        return null;
                    return { topic: label, schema: parsed as Record<string, unknown> };
                } catch {
                    return null;
                }
            }),
        );
        return results
            .filter((r): r is PromiseFulfilledResult<TopicEntry> => r.status === "fulfilled" && r.value !== null)
            .map((r) => r.value);
    }, [processName, processProperties]);

    const fetchTopicDefinitions = fetchTopicDefinitionsOverride ?? defaultFetchTopicDefinitions;

    const handleOpenTopicPicker = useCallback(async () => {
        setShowTopicPicker(true);
        setShowTargetSample(false);
        if (topicEntries.length > 0) return;
        setTopicsLoading(true);
        try {
            const entries = await fetchTopicDefinitions();
            setTopicEntries(entries);
        } finally {
            setTopicsLoading(false);
        }
    }, [fetchTopicDefinitions, topicEntries.length]);

    const applyTopicSchema = useCallback((entry: TopicEntry) => {
        const newFields = fieldsFromSample(entry.schema);
        if (newFields.length === 0) return;
        setFields(newFields.map((f) => ({ ...f, id: nextId() })));
        setShowTopicPicker(false);
    }, []);

    return {
        // state
        enrichedContext,
        fields,
        selField,
        selPath,
        dragOverId,
        showTargetSample,
        showContextSample,
        showTopicPicker,
        topicEntries,
        topicsLoading,
        contextFilter,
        dropZoneActive,
        mappedCount,
        // setters
        setSelField,
        setDragOverId,
        setShowTargetSample,
        setShowContextSample,
        setShowTopicPicker,
        setContextFilter,
        setDropZoneActive,
        // callbacks
        spelOutput,
        addField,
        addFieldFromDrop,
        removeField,
        updateField,
        moveField,
        addMapEntry,
        handleAutoMap,
        onTreeSelect,
        onDrop,
        applyTargetSample,
        applyContextSample,
        handleOpenTopicPicker,
        applyTopicSchema,
    };
}
