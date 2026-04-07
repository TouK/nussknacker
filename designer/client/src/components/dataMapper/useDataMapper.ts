import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import HttpService from "../../http/HttpService/instance";
import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import { useAppSelector } from "../../store/storeHelpers";
import type { VariableTypes } from "../../types/validation";
import { toNullSafe, typingResultToSample } from "../builderComponents/typeUtils";
import type { FieldError } from "../graph/node-modal/editors/Validators";
import { getProcessName, getProcessProperties } from "../graph/node-modal/NodeDetailsContent/selectors";
import type { ContextData, FieldDef, TopicEntry } from "./dataMapperUtils";
import {
    applyTypeConversion,
    checkTypeCompatibility,
    EXPR_PROBE_NODE_BASE,
    fieldsFromSample,
    genSpelFromFields,
    getNuTypeAtPath,
    INITIAL_FIELDS,
    makeField,
    nextId,
    parseSpelToFields,
    refClazzToNuType,
    SAMPLE_CONTEXT,
} from "./dataMapperUtils";

interface UseDataMapperOptions {
    initialContext?: ContextData;
    initialExpression?: string;
    initialFields?: FieldDef[];
    initialFocusFieldName?: string;
    variableTypes?: VariableTypes;
    isEmbedded: boolean;
    fetchTopicDefinitionsOverride?: () => Promise<TopicEntry[]>;
}

// ─── Tree helpers ─────────────────────────────────────────────────────────────

function updateInTree(fields: FieldDef[], id: number, updater: (f: FieldDef) => FieldDef): FieldDef[] {
    return fields.map((f) => {
        if (f.id === id) return updater(f);
        if (f.children.length > 0) return { ...f, children: updateInTree(f.children, id, updater) };
        return f;
    });
}

function removeFromTree(fields: FieldDef[], id: number): FieldDef[] {
    return fields.filter((f) => f.id !== id).map((f) => (f.children.length > 0 ? { ...f, children: removeFromTree(f.children, id) } : f));
}

function moveInTree(fields: FieldDef[], id: number, dir: 1 | -1): FieldDef[] {
    const idx = fields.findIndex((f) => f.id === id);
    if (idx >= 0) {
        const ni = idx + dir;
        if (ni < 0 || ni >= fields.length) return fields;
        const c = [...fields];
        [c[idx], c[ni]] = [c[ni], c[idx]];
        return c;
    }
    return fields.map((f) => (f.children.length > 0 ? { ...f, children: moveInTree(f.children, id, dir) } : f));
}

function findInTree(fields: FieldDef[], id: number): FieldDef | undefined {
    for (const f of fields) {
        if (f.id === id) return f;
        if (f.children.length > 0) {
            const found = findInTree(f.children, id);
            if (found) return found;
        }
    }
    return undefined;
}

/** Finds a FieldDef by a dotted path (e.g. "combinedState.tool_record"), traversing children. */
function findFieldByDottedPath(fields: FieldDef[], path: string): FieldDef | undefined {
    const segments = path.split(".");
    let current = fields;
    let match: FieldDef | undefined;
    for (const seg of segments) {
        match = current.find((f) => f.name === seg);
        if (!match) return undefined;
        current = match.children;
    }
    return match;
}

/** When expr contains an Elvis fallback (e.g. from applyTypeConversion), split it into expression + defaultValue. */
function splitElvisIntoField(field: FieldDef, expr: string): FieldDef {
    const elvisIdx = expr.lastIndexOf(" ?: ");
    if (elvisIdx >= 0) {
        return { ...field, expression: expr.slice(0, elvisIdx), defaultValue: expr.slice(elvisIdx + 4) };
    }
    return { ...field, expression: expr };
}

function mergeTypesFromSchema(parsed: FieldDef[], schema: FieldDef[]): FieldDef[] {
    return parsed.map((f) => {
        const schemaField = schema.find((s) => s.name === f.name);
        if (!schemaField) return f;
        if (f.isRecord && schemaField.children.length > 0) {
            return { ...f, type: schemaField.type, children: mergeTypesFromSchema(f.children, schemaField.children) };
        }
        if (f.type === "Any" && schemaField.type !== "Any") {
            return { ...f, type: schemaField.type };
        }
        return f;
    });
}

// eslint-disable-next-line @typescript-eslint/explicit-module-boundary-types
export function useDataMapper({
    initialContext,
    initialExpression,
    initialFields: initialFieldsProp,
    initialFocusFieldName,
    variableTypes,
    isEmbedded,
    fetchTopicDefinitionsOverride,
}: UseDataMapperOptions) {
    const processName = useAppSelector(getProcessName);
    const processProperties = useAppSelector(getProcessProperties);
    const processDefinitionData = useAppSelector(getProcessDefinitionData);

    const [context, setContext] = useState<ContextData>(() => initialContext ?? (variableTypes ? {} : SAMPLE_CONTEXT));
    const [fields, setFields] = useState<FieldDef[]>(() => {
        if (initialExpression) {
            const parsed = parseSpelToFields(initialExpression);
            if (parsed) {
                if (initialFieldsProp?.length) {
                    return mergeTypesFromSchema(parsed, initialFieldsProp);
                }
                return parsed;
            }
        }
        if (initialFieldsProp && initialFieldsProp.length > 0) {
            return initialFieldsProp.map((f) => {
                const elvisIdx = f.expression.lastIndexOf(" ?: ");
                if (elvisIdx >= 0) {
                    return { ...f, expression: f.expression.slice(0, elvisIdx), defaultValue: f.expression.slice(elvisIdx + 4) };
                }
                return f;
            });
        }
        return isEmbedded ? [] : INITIAL_FIELDS.map((f) => ({ ...f, id: nextId() }));
    });
    const [selField, setSelField] = useState<number | null>(null);

    const initialFocusFieldsRef = useRef(fields);
    useEffect(() => {
        if (!initialFocusFieldName) return;
        const match = findFieldByDottedPath(initialFocusFieldsRef.current, initialFocusFieldName);
        if (match) setSelField(match.id);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const [selPath, setSelPath] = useState<string | null>(null);
    const [dragOverId, setDragOverId] = useState<number | null>(null);
    const [showTargetSample, setShowTargetSample] = useState(false);
    const [showContextSample, setShowContextSample] = useState(false);
    const [showTopicPicker, setShowTopicPicker] = useState(false);
    const [topicEntries, setTopicEntries] = useState<TopicEntry[]>([]);
    const [topicsLoading, setTopicsLoading] = useState(false);
    const [contextFilter, setContextFilter] = useState("");
    const [dropZoneActive, setDropZoneActive] = useState(false);
    const [fieldErrors, setFieldErrors] = useState<Record<number, FieldError[]>>({});
    const [nullSafe, setNullSafe] = useState(true);

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

    const mappedCount = useMemo(() => fields.filter((f) => f.isRecord || f.expression).length, [fields]);
    const spelOutput = useCallback(() => genSpelFromFields(fields), [fields]);

    const addField = useCallback(() => setFields((f) => [...f, makeField()]), []);

    const addFieldFromDrop = useCallback(
        (path: string) => {
            const rawPath = path.replace(/^#/, "").replace(/\?/g, "");
            const bracketMatch = rawPath.match(/\['([^']+)'\]$/);
            const lastSegment = bracketMatch ? bracketMatch[1] : rawPath.split(".").pop() ?? "";
            const sourceType = variableTypes ? getNuTypeAtPath(variableTypes, rawPath) : undefined;
            const field = makeField(lastSegment, sourceType ?? "Any");
            field.expression = nullSafe ? toNullSafe(`#${rawPath}`) : `#${rawPath}`;
            setFields((f) => [...f, field]);
            setDragOverId(null);
            setDropZoneActive(false);
        },
        [nullSafe, variableTypes],
    );

    const removeField = useCallback(
        (id: number) => {
            setFields((f) => removeFromTree(f, id));
            if (selField === id) setSelField(null);
        },
        [selField],
    );

    const updateField = useCallback((id: number, key: keyof FieldDef, val: unknown) => {
        setFields((f) => updateInTree(f, id, (x) => ({ ...x, [key]: val })));
    }, []);

    const moveField = useCallback((id: number, dir: 1 | -1) => {
        setFields((f) => moveInTree(f, id, dir));
    }, []);

    const addChildField = useCallback((parentId: number) => {
        setFields((fs) => updateInTree(fs, parentId, (f) => ({ ...f, children: [...f.children, makeField()] })));
    }, []);

    const clearFieldErrors = useCallback((id: number) => {
        setFieldErrors((prev) => {
            if (!(id in prev)) return prev;
            const next = { ...prev };
            delete next[id];
            return next;
        });
    }, []);

    const validateExpression = useCallback(
        async (id: number, expression: string, nuType?: FieldDef["type"]) => {
            if (!processName || !processProperties) return;
            if (!expression.trim()) {
                clearFieldErrors(id);
                return;
            }
            const nodeData = { ...EXPR_PROBE_NODE_BASE, value: { language: "spel", expression } };
            const result = await HttpService.validateNode(processName, {
                nodeData: nodeData as never,
                variableTypes: variableTypes ?? {},
                branchVariableTypes: {},
                outgoingEdges: [],
                testCases: {},
                processProperties,
            });
            if (!result) return;
            const errors: FieldError[] = result.validationErrors.map(({ message, description, details }) => ({
                message,
                description,
                details,
            }));
            if (errors.length === 0) {
                if (nuType && nuType !== "Any") {
                    const typeMismatch = checkTypeCompatibility(result.expressionType, nuType);
                    if (typeMismatch) {
                        errors.push({ message: typeMismatch, description: null, details: null });
                    }
                } else if (nuType === "Any" && result.expressionType) {
                    const inferred = refClazzToNuType(result.expressionType.refClazzName);
                    if (inferred !== "Any") {
                        setFields((prev) => updateInTree(prev, id, (f) => (f.type === "Any" ? { ...f, type: inferred } : f)));
                    }
                }
            }
            setFieldErrors((prev) => ({ ...prev, [id]: errors }));
        },
        [processName, processProperties, variableTypes, clearFieldErrors],
    );

    const handleAutoMap = useCallback(() => {
        const pathMap = new Map<string, string>();
        function traverse(obj: unknown, path: string, typing: { fields?: Record<string, unknown> } | undefined) {
            if (obj !== null && typeof obj === "object" && !Array.isArray(obj)) {
                Object.entries(obj as Record<string, unknown>).forEach(([k, v]) => {
                    const childTyping = typing?.fields?.[k] as { fields?: Record<string, unknown> } | undefined;
                    const segment = childTyping !== undefined ? `.${k}` : `['${k}']`;
                    traverse(v, `${path}${segment}`, childTyping);
                });
            } else {
                const bracketMatch = path.match(/\['([^']+)'\]$/);
                const key = (bracketMatch ? bracketMatch[1] : path.split(".").pop() ?? "").toLowerCase().replace(/[_\s]/g, "");
                if (!pathMap.has(key)) pathMap.set(key, path);
            }
        }
        Object.entries(enrichedContext).forEach(([key, val]) =>
            traverse(val, key, variableTypes?.[key] as { fields?: Record<string, unknown> } | undefined),
        );

        function autoMap(fs: FieldDef[]): FieldDef[] {
            return fs.map((f) => {
                if (f.isRecord) return { ...f, children: autoMap(f.children) };
                if (f.expression) return f;
                const normalized = f.name.toLowerCase().replace(/[_\s]/g, "");
                const match = pathMap.get(normalized);
                const expr = `#${match}`;
                return match ? { ...f, expression: nullSafe ? toNullSafe(expr) : expr } : f;
            });
        }
        setFields(autoMap);
    }, [enrichedContext, nullSafe, variableTypes]);

    const onTreeSelect = useCallback(
        (path: string) => {
            setSelPath(path);
            if (selField != null) {
                const baseExpr = nullSafe ? toNullSafe(`#${path}`) : `#${path}`;
                const sourceType = variableTypes ? getNuTypeAtPath(variableTypes, path) : undefined;
                setFields((f) => {
                    const targetField = findInTree(f, selField);
                    const expr = applyTypeConversion(baseExpr, sourceType, targetField?.type ?? "Any", targetField?.defaultValue);
                    return updateInTree(f, selField, (x) => splitElvisIntoField(x, expr));
                });
            }
        },
        [selField, nullSafe, variableTypes],
    );

    const onDrop = useCallback(
        (path: string, fieldId: number) => {
            const rawPath = path.replace(/^#/, "").replace(/\?/g, "");
            const bracketMatch = rawPath.match(/\['([^']+)'\]$/);
            const lastSegment = bracketMatch ? bracketMatch[1] : rawPath.split(".").pop() ?? "";
            const baseExpr = nullSafe ? toNullSafe(`#${rawPath}`) : `#${rawPath}`;
            const sourceType = variableTypes ? getNuTypeAtPath(variableTypes, rawPath) : undefined;
            setFields((prev) => {
                const targetField = findInTree(prev, fieldId);
                const expr = applyTypeConversion(baseExpr, sourceType, targetField?.type ?? "Any", targetField?.defaultValue);
                return updateInTree(prev, fieldId, (x) => {
                    const nameUpdate = !x.name?.trim() && lastSegment ? { name: lastSegment } : {};
                    return { ...splitElvisIntoField(x, expr), ...nameUpdate };
                });
            });
            setDragOverId(null);
        },
        [nullSafe, variableTypes],
    );

    const applyTargetSample = useCallback((parsed: unknown, mode: "replace" | "merge"): string | null => {
        const newFields = fieldsFromSample(parsed);
        if (newFields.length === 0) return 'JSON must be a flat object, e.g. {"name": "value"}';
        if (mode === "replace") {
            setFields(newFields);
        } else {
            setFields((prev) => {
                const existing = new Set(prev.map((x) => x.name));
                const toAdd = newFields.filter((nf) => !existing.has(nf.name));
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
        if (!processName || !processProperties || !processDefinitionData) return [];

        // Find all configured Kafka sink component templates
        const kafkaSinks = (processDefinitionData.componentGroups ?? [])
            .flatMap((g) => g.components)
            .filter((c) => {
                const ref = (c.node as unknown as { ref?: { typ?: string } }).ref;
                return c.node.type === "Sink" && ref?.typ?.endsWith("kafka");
            });

        if (kafkaSinks.length === 0) return [];

        const allEntries = await Promise.all(
            kafkaSinks.map(async (c) => {
                const sourceLabel = c.label === "Topic" ? "Default Kafka" : c.label;
                const ref = (c.node as unknown as { ref: { typ: string; parameters: Array<{ name: string; expression: unknown }> } }).ref;
                const probeBase = { ...c.node, id: "_spel-mapper-probe", name: "" };

                // Step 1: get topic list for this kafka type
                const probe = await HttpService.validateNode(processName, {
                    nodeData: probeBase as never,
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

                // Step 2: for each topic fetch its Value schema
                const results = await Promise.allSettled(
                    topics.map(async ({ expression, label }) => {
                        const nodeWithTopic = {
                            ...probeBase,
                            ref: {
                                ...ref,
                                parameters: ref.parameters.map((p) =>
                                    p.name === "Topic" ? { ...p, expression: { language: "spel", expression } } : p,
                                ),
                            },
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

                        // Standard Kafka sink params that are not Avro value fields
                        const KAFKA_SYSTEM_PARAMS = new Set([
                            "Topic",
                            "Schema version",
                            "Key",
                            "Raw editor",
                            "Content type",
                            "Value validation mode",
                            "Value",
                        ]);

                        // Case 1: single "Value" param (raw editor mode) — use its typing result
                        const valueParam = data.parameters?.find((p) => p.name === "Value");
                        if (valueParam?.typ) {
                            const sample = typingResultToSample(valueParam.typ);
                            if (typeof sample === "object" && sample !== null && !Array.isArray(sample) && Object.keys(sample).length > 0) {
                                return { topic: label, schema: sample as Record<string, unknown>, sourceLabel };
                            }
                        }

                        // Case 2: individual Avro field params (dynamic mode) — build schema from them
                        const fieldParams = (data.parameters ?? []).filter((p) => !KAFKA_SYSTEM_PARAMS.has(p.name));
                        if (fieldParams.length > 0) {
                            const schema = Object.fromEntries(fieldParams.map((p) => [p.name, typingResultToSample(p.typ)]));
                            return { topic: label, schema, sourceLabel };
                        }

                        // Case 3: fallback — parse defaultValue as JSON
                        const defaultExpr = valueParam?.defaultValue?.expression;
                        if (!defaultExpr) return null;
                        try {
                            const parsed = JSON.parse(defaultExpr);
                            if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed) || Object.keys(parsed).length === 0)
                                return null;
                            return { topic: label, schema: parsed as Record<string, unknown>, sourceLabel };
                        } catch {
                            return null;
                        }
                    }),
                );
                return results
                    .filter((r) => r.status === "fulfilled" && r.value !== null)
                    .map((r) => (r as PromiseFulfilledResult<TopicEntry>).value);
            }),
        );
        return allEntries.flat();
    }, [processName, processProperties, processDefinitionData]);

    const fetchTopicDefinitions = fetchTopicDefinitionsOverride ?? defaultFetchTopicDefinitions;

    const fetchTopicDefinitionsRef = useRef(fetchTopicDefinitions);
    fetchTopicDefinitionsRef.current = fetchTopicDefinitions;

    useEffect(() => {
        if (!initialExpression || initialFieldsProp?.length) return;
        fetchTopicDefinitionsRef.current().then((entries) => {
            if (!entries.length) return;
            setTopicEntries(entries);
            const fieldNames = new Set(initialFocusFieldsRef.current.map((f) => f.name));
            let bestEntry: TopicEntry | null = null;
            let bestScore = 0;
            for (const entry of entries) {
                const score = fieldsFromSample(entry.schema).filter((f) => fieldNames.has(f.name)).length;
                if (score > bestScore) {
                    bestScore = score;
                    bestEntry = entry;
                }
            }
            if (bestEntry && bestScore > 0) {
                const schemaFields = fieldsFromSample(bestEntry.schema);
                setFields((prev) => mergeTypesFromSchema(prev, schemaFields));
            }
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

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
        setFields(newFields);
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
        fieldErrors,
        nullSafe,
        // setters
        setSelField,
        setDragOverId,
        setShowTargetSample,
        setShowContextSample,
        setShowTopicPicker,
        setContextFilter,
        setDropZoneActive,
        setNullSafe,
        // callbacks
        spelOutput,
        addField,
        addFieldFromDrop,
        removeField,
        updateField,
        moveField,
        addChildField,
        validateExpression,
        clearFieldErrors,
        handleAutoMap,
        onTreeSelect,
        onDrop,
        applyTargetSample,
        applyContextSample,
        handleOpenTopicPicker,
        applyTopicSchema,
    };
}
