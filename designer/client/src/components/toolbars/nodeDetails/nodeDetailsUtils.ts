import ProcessUtils from "../../../common/ProcessUtils";
import { ParameterCategory } from "../../../types/definition";
import type { UIParameter } from "../../../types/definition";
import type { Edge } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import type { ComponentDefinition } from "../../../types/scenarioGraph";
import { EditorType } from "../../graph/node-modal/editors/expression/types";

export interface NodeSummaryContext {
    edges: Edge[];
    nodes: NodeType[];
}

export interface SummaryItem {
    label: string;
    value: string;
}

// ─── Value formatting ──────────────────────────────────────────────────────────

const DURATION_EXPR_RE = /^T\(java\.time\.(?:Duration|Period)\)\.parse\('([^']+)'\)$/;
const ENUM_EXPR_RE = /^T\([^)]+\)\.([A-Za-z][A-Za-z0-9_]*)$/;

function formatIso8601Duration(iso: string): string {
    const m = iso.match(/^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?)?$/);
    if (!m) return iso;
    const [, years, months, weeks, days, hours, mins, secs] = m;
    const parts: string[] = [];
    if (years) parts.push(`${years}y`);
    if (months) parts.push(`${months}mo`);
    if (weeks) parts.push(`${weeks}w`);
    if (days) parts.push(`${days}d`);
    if (hours) parts.push(`${hours}h`);
    if (mins) parts.push(`${mins}m`);
    if (secs) {
        const s = parseFloat(secs);
        parts.push(`${s % 1 === 0 ? Math.round(s) : s}s`);
    }
    return parts.join(" ") || iso;
}

function formatEnumExpression(value: string): string | null {
    const m = value.match(ENUM_EXPR_RE);
    if (!m) return null;
    const name = m[1];
    // SCREAMING_SNAKE_CASE: ON_EACH_EVENT → "On each event"
    if (name.includes("_")) {
        return name
            .split("_")
            .map((word, i) => (i === 0 ? word.charAt(0).toUpperCase() + word.slice(1).toLowerCase() : word.toLowerCase()))
            .join(" ");
    }
    // PascalCase: OnEvent → "On event", AfterWindowCloses → "After window closes"
    return name
        .replace(/([A-Z])/g, " $1")
        .trim()
        .split(" ")
        .map((word, i) => (i === 0 ? word.charAt(0).toUpperCase() + word.slice(1).toLowerCase() : word.toLowerCase()))
        .join(" ");
}

/** Format a raw SpEL expression value into a human-readable string.
 *  @param label - parameter name, used to add "every" prefix for schedule params */
export function formatValue(label: string, value: string): string {
    const durationMatch = value.match(DURATION_EXPR_RE);
    if (durationMatch) {
        const formatted = formatIso8601Duration(durationMatch[1]);
        return label.toLowerCase() === "schedule" ? `every ${formatted}` : formatted;
    }
    const enumFormatted = formatEnumExpression(value);
    if (enumFormatted) return enumFormatted;
    // Strip surrounding single quotes from SpEL string literals: 'foo' → foo
    if (value.startsWith("'") && value.endsWith("'") && value.length >= 2) {
        return value.slice(1, -1);
    }
    return value;
}

// ─── Parameter helpers ────────────────────────────────────────────────────────

type RawParam = { name: string; expression: { expression: string } };

/** Parameters that are too complex / irrelevant to show in the summary panel. */
const HIDDEN_PARAM_NAMES = new Set(["aggregator", "aggregateBy", "body", "Decision Table"]);

function paramItems(parameters: RawParam[] | undefined, nameFilter?: (name: string) => boolean, includeEmpty = false): SummaryItem[] {
    if (!parameters?.length) return [];
    const filtered = nameFilter ? parameters.filter((p) => nameFilter(p.name)) : parameters;
    const mapped = filtered.map((p) => ({ label: p.name, value: p.expression?.expression ?? "" }));
    return includeEmpty ? mapped : mapped.filter((item) => item.value !== "");
}

function outputVarItem(node: NodeType): SummaryItem[] {
    const outputVar = node.outputVar ?? node.output ?? node.varName ?? node.outputName;
    if (!outputVar) return [];
    return [{ label: "output variable", value: `#${outputVar}` }];
}

function isTopicParam(name: string): boolean {
    return name.toLowerCase().includes("topic");
}

function standardParamItems(
    parameters: RawParam[] | undefined,
    componentDef: ComponentDefinition | null,
    hiddenNames: Set<string> = HIDDEN_PARAM_NAMES,
): SummaryItem[] {
    if (!parameters?.length) return [];
    const paramDefs = componentDef?.parameters ?? [];
    return parameters
        .filter((p) => {
            if (hiddenNames.has(p.name)) return false;
            const def = paramDefs.find((d) => d.name === p.name);
            return !def || def.category !== ParameterCategory.Advanced;
        })
        .map((p) => ({ label: p.name, value: p.expression?.expression ?? "" }))
        .filter((item) => item.value !== "");
}

// ─── Per-type config ──────────────────────────────────────────────────────────

type NodeSummaryFn = (node: NodeType, componentDef: ComponentDefinition | null, context: NodeSummaryContext) => SummaryItem[];

const NODE_SUMMARY_CONFIG: Partial<Record<string, NodeSummaryFn>> = {
    Filter: (n, _def, { edges, nodes }) => {
        const items: SummaryItem[] = [];
        if (n.expression?.expression) items.push({ label: "condition", value: n.expression.expression });
        const outgoing = edges.filter((e) => e.from === n.id);
        for (const e of outgoing) {
            const targetName = `-> ${nodes.find((nd) => nd.id === e.to)?.name ?? e.to}`;
            if (e.edgeType?.type === EdgeKind.filterTrue) items.push({ label: "true", value: targetName });
            if (e.edgeType?.type === EdgeKind.filterFalse) items.push({ label: "false", value: targetName });
        }
        return items;
    },

    Switch: (n, _def, { edges, nodes }) => {
        const outgoing = edges.filter((e) => e.from === n.id);
        return outgoing.flatMap((e) => {
            const targetName = `-> ${nodes.find((nd) => nd.id === e.to)?.name ?? e.to}`;
            if (e.edgeType?.type === EdgeKind.switchNext && e.edgeType.condition?.expression) {
                return [{ label: targetName, value: e.edgeType.condition.expression }];
            }
            if (e.edgeType?.type === EdgeKind.switchDefault) {
                return [{ label: targetName, value: "default" }];
            }
            return [];
        });
    },

    Variable: (n) => [...outputVarItem(n), { label: "value", value: n.value?.expression ?? "" }].filter((i) => i.value !== ""),

    MapVariable: (n) => outputVarItem(n),

    Source: (n, def) => {
        const params = n.ref?.parameters ?? [];
        const topicItems = paramItems(params, isTopicParam, true);
        if (topicItems.length > 0) return [...topicItems, ...outputVarItem(n)];
        const urlItems = paramItems(params, (name) => name.toLowerCase() === "url" || name.toLowerCase() === "endpoint", true);
        if (urlItems.length > 0) return [...urlItems, ...outputVarItem(n)];
        const isEventGenerator = n.ref?.typ?.toLowerCase().includes("event-generator");
        const eventGeneratorHidden = isEventGenerator ? new Set([...HIDDEN_PARAM_NAMES, "value"]) : HIDDEN_PARAM_NAMES;
        const standardItems = standardParamItems(params, def, eventGeneratorHidden);
        // Fallback: if nothing useful found, show ref.typ as topic (Kafka per-topic components)
        if (standardItems.length === 0 && n.ref?.typ) {
            return [{ label: "topic", value: n.ref.typ }, ...outputVarItem(n)];
        }
        return [...standardItems, ...outputVarItem(n)].filter((i) => i.value !== "");
    },

    Sink: (n, def) => {
        const params = n.ref?.parameters ?? [];
        const topicItems = paramItems(params, isTopicParam, true);
        if (topicItems.length > 0) return topicItems;
        return standardParamItems(params, def).filter((i) => i.value !== "");
    },

    Enricher: (n, def) => {
        const params = n.service?.parameters ?? [];
        const urlItems = paramItems(params, (name) => name.toLowerCase() === "url" || name.toLowerCase() === "endpoint", true);
        if (urlItems.length > 0) return [...urlItems, ...outputVarItem(n)];
        // OpenAPI / lookup enrichers: always show service id + all dynamic params (including empty)
        const serviceId = n.service?.id ?? "";
        if (serviceId.endsWith("openAPI") || serviceId.endsWith("lookup")) {
            return [{ label: "service", value: serviceId }, ...paramItems(params, undefined, true), ...outputVarItem(n)];
        }
        return [...standardParamItems(params, def), ...outputVarItem(n)].filter((i) => i.value !== "");
    },

    Processor: (n, def) => {
        const params = n.service?.parameters ?? [];
        const urlItems = paramItems(params, (name) => name.toLowerCase() === "url" || name.toLowerCase() === "endpoint", true);
        if (urlItems.length > 0) return urlItems;
        return standardParamItems(params, def).filter((i) => i.value !== "");
    },

    CustomNode: (n, def) => {
        const params = n.parameters ?? [];
        const nodeType = n.nodeType ?? "";
        // Decision table: show only matchCondition + output variable
        if (nodeType === "decision-table") {
            const matchItems = params
                .filter((p) => p.name.toLowerCase().replace(/[\s_-]/g, "") === "matchcondition")
                .map((p) => ({ label: p.name, value: p.expression?.expression ?? "" }));
            return [...matchItems, ...outputVarItem(n)].filter((i) => i.value !== "");
        }
        // Union Memo: show key + value + stateTimeout + groupBy
        if (nodeType === "union-memo") {
            return paramItems(params, (name) => {
                const normalized = name.toLowerCase().replace(/[\s_-]/g, "");
                return normalized === "key" || normalized === "value" || normalized === "statetimeout" || normalized === "groupby";
            });
        }
        // Aggregate Session: show emitWhen + endSessionCondition + sessionTimeout + groupBy + key + outputVar
        if (nodeType === "aggregate-session") {
            return [
                ...paramItems(params, (name) => {
                    const normalized = name.toLowerCase().replace(/[\s_-]/g, "");
                    return (
                        normalized === "emitwhen" ||
                        normalized === "endsessioncondition" ||
                        normalized === "sessiontimeout" ||
                        normalized === "groupby" ||
                        normalized === "key"
                    );
                }),
                ...outputVarItem(n),
            ].filter((i) => i.value !== "");
        }
        // Aggregate Sliding: show emitWhen + emitWhenEventLeft + windowLength + groupBy + key + outputVar
        if (nodeType === "aggregate-sliding") {
            return [
                ...paramItems(params, (name) => {
                    const normalized = name.toLowerCase().replace(/[\s_-]/g, "");
                    return (
                        normalized === "emitwhen" ||
                        normalized === "emitwheneventleft" ||
                        normalized === "windowlength" ||
                        normalized === "groupby" ||
                        normalized === "key"
                    );
                }),
                ...outputVarItem(n),
            ].filter((i) => i.value !== "");
        }
        // Aggregate Tumbling: show emitWhen + windowLength + groupBy + key + outputVar
        if (nodeType === "aggregate-tumbling") {
            return [
                ...paramItems(params, (name) => {
                    const normalized = name.toLowerCase().replace(/[\s_-]/g, "");
                    return normalized === "emitwhen" || normalized === "windowlength" || normalized === "groupby" || normalized === "key";
                }),
                ...outputVarItem(n),
            ].filter((i) => i.value !== "");
        }
        // Delay: show key + delay duration + groupBy
        if (nodeType === "delay") {
            return paramItems(params, (name) => {
                const normalized = name.toLowerCase().replace(/[\s_-]/g, "");
                return normalized === "key" || normalized === "delay" || normalized === "groupby";
            });
        }
        return [...outputVarItem(n), ...standardParamItems(params, def)].filter((i) => i.value !== "");
    },

    FragmentInput: (n) => {
        const base: SummaryItem[] = [{ label: "ref", value: n.ref?.id ?? "" }];
        const inputParams = paramItems(n.ref?.parameters);
        inputParams.forEach((item) => base.push(item));
        const outputVarNames = n.ref?.outputVariableNames;
        if (outputVarNames) {
            Object.entries(outputVarNames).forEach(([k, v]) =>
                base.push({ label: k === "output" ? "output variable" : `output.${k}`, value: `#${v}` }),
            );
        }
        return base.filter((i) => i.value !== "");
    },
};

function findFixedValueLabel(paramDef: UIParameter | undefined, expression: string): string | null {
    if (!paramDef?.editors) return null;
    for (const editor of paramDef.editors) {
        if (
            (editor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR ||
                editor.type === EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR ||
                editor.type === EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR) &&
            "possibleValues" in editor
        ) {
            const match = editor.possibleValues.find((v) => v.expression === expression);
            if (match) return match.label;
        }
    }
    return null;
}

export function getNodeSummaryItems(
    node: NodeType,
    components: Record<string, ComponentDefinition> | undefined,
    context: NodeSummaryContext = { edges: [], nodes: [] },
): SummaryItem[] {
    const componentDef = components ? ProcessUtils.extractComponentDefinition(node, components) : null;
    const specific = NODE_SUMMARY_CONFIG[node.type];
    const items = specific
        ? specific(node, componentDef, context)
        : [
              ...outputVarItem(node),
              ...standardParamItems(node.parameters ?? node.ref?.parameters ?? node.service?.parameters ?? [], componentDef),
          ];

    return items.map((item) => {
        const paramDef = componentDef?.parameters?.find((p) => p.name === item.label);
        const fixedLabel = findFixedValueLabel(paramDef, item.value);
        return { ...item, value: fixedLabel ?? formatValue(item.label, item.value) };
    });
}
