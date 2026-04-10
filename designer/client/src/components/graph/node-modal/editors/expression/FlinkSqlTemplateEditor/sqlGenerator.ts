import type { AfterMatchStrategy, CepState, DedupState, InputField, VisualEditorState, WindowDedupState, WindowTopNState } from "./types";

function buildSelectClause(fields: InputField[]): string {
    const selectedFields = fields.filter((f) => f.selected);
    const lines = selectedFields.map((f) => `    ${f.source} AS ${f.alias}`);
    lines.push("    record_time");
    return lines.join(",\n");
}

function buildInnerSelect(fields: InputField[]): string {
    return `  SELECT\n${buildSelectClause(fields)}\n  FROM record`;
}

function formatAfterMatchStrategy(strategy: AfterMatchStrategy): string {
    switch (strategy.type) {
        case "SKIP PAST LAST ROW":
            return "SKIP PAST LAST ROW";
        case "SKIP TO NEXT ROW":
            return "SKIP TO NEXT ROW";
        case "SKIP TO FIRST":
            return `SKIP TO FIRST ${strategy.variable}`;
        case "SKIP TO LAST":
            return `SKIP TO LAST ${strategy.variable}`;
    }
}

export function generateCepSql(fields: InputField[], config: CepState): string {
    const selectedFields = fields.filter((f) => f.selected);
    if (selectedFields.length === 0) return "-- Select input fields first";

    const patternClause = config.pattern
        .map((pv) => {
            const q = pv.quantifier ? `${pv.name}${pv.quantifier}` : pv.name;
            return q;
        })
        .join(" ");

    const measuresLines = config.measures
        .map((m) => {
            const expr = m.func ? `${m.func}(${m.expression})` : m.expression;
            return `        ${expr} AS ${m.alias}`;
        })
        .join(",\n");

    const defineLines = config.pattern
        .map((pv) => {
            const conditions = pv.conditions
                .filter((c) => {
                    if (c.mode === "simple") return c.field || c.value;
                    return c.expression.trim().length > 0;
                })
                .map((c) => {
                    if (c.mode === "expr") {
                        return c.expression;
                    }
                    if (c.operator === "IS NULL" || c.operator === "IS NOT NULL") {
                        return `${pv.name}.${c.field} ${c.operator}`;
                    }
                    return `${pv.name}.${c.field} ${c.operator} ${c.value}`;
                })
                .join(`\n          AND `);
            return `        ${pv.name} AS ${conditions || "TRUE"}`;
        })
        .join(",\n");

    const partitionBy = config.partitionBy ? `    PARTITION BY ${config.partitionBy}\n` : "";
    const orderBy = config.orderBy ? `    ORDER BY ${config.orderBy}\n` : "";

    const rowsPerMatch = config.matchOptions?.rowsPerMatch ?? "ONE ROW PER MATCH";
    const afterMatchStr = formatAfterMatchStrategy(config.matchOptions?.afterMatch ?? { type: "SKIP PAST LAST ROW" });

    const withinClause = config.within ? ` WITHIN INTERVAL '${config.within.split(" ")[0]}' ${config.within.split(" ")[1]}` : "";

    return `SELECT *
FROM (
${buildInnerSelect(fields)}
)
MATCH_RECOGNIZE (
${partitionBy}${orderBy}    MEASURES
${measuresLines || "        -- add measures"}
    ${rowsPerMatch}
    AFTER MATCH ${afterMatchStr}
    PATTERN (${patternClause})${withinClause}
    DEFINE
${defineLines}
) AS match_result`;
}

export function generateDedupSql(fields: InputField[], config: DedupState): string {
    const selectedFields = fields.filter((f) => f.selected);
    if (selectedFields.length === 0) return "-- Select input fields first";

    const partitionBy = config.partitionBy.length > 0 ? config.partitionBy.join(", ") : "-- partition key";
    const outputCols = [...selectedFields.map((f) => f.alias), "record_time"].join(", ");

    return `SELECT ${outputCols}
FROM (
  SELECT *,
    ROW_NUMBER() OVER (
      PARTITION BY ${partitionBy}
      ORDER BY PROCTIME() ASC
    ) AS rn
  FROM (
${buildInnerSelect(fields)}
  )
)
WHERE rn = 1`;
}

function formatInterval(windowSize: string): string {
    const [value, ...unitParts] = windowSize.split(" ");
    return `INTERVAL '${value}' ${unitParts.join(" ")}`;
}

function buildWindowTumbleClause(fields: InputField[], windowSize: string): string {
    return `TABLE(TUMBLE(\n    (\n${buildInnerSelect(fields)}\n    ),\n    DESCRIPTOR(record_time),\n    ${formatInterval(
        windowSize,
    )}\n  ))`;
}

export function generateWindowDedupSql(fields: InputField[], config: WindowDedupState): string {
    const selectedFields = fields.filter((f) => f.selected);
    if (selectedFields.length === 0) return "-- Select input fields first";

    const partitionByCols =
        config.partitionBy.length > 0 ? `window_start, window_end, ${config.partitionBy.join(", ")}` : "window_start, window_end";
    // Explicit column list (not SELECT *) to exclude window_time from output.
    // window_time is a rowtime attribute added by TUMBLE; keeping only record_time avoids dual rowtime in sinks.
    const outputCols = [...selectedFields.map((f) => f.alias), "record_time", "window_start", "window_end"].join(", ");

    return `SELECT ${outputCols}
FROM (
  SELECT *,
    ROW_NUMBER() OVER (
      PARTITION BY ${partitionByCols}
      ORDER BY record_time ASC
    ) AS rn
  FROM ${buildWindowTumbleClause(fields, config.windowSize)}
)
WHERE rn = 1`;
}

export function generateWindowTopNSql(fields: InputField[], config: WindowTopNState): string {
    const selectedFields = fields.filter((f) => f.selected);
    if (selectedFields.length === 0) return "-- Select input fields first";

    const partitionByCols =
        config.partitionBy.length > 0 ? `window_start, window_end, ${config.partitionBy.join(", ")}` : "window_start, window_end";
    const orderByClause = config.orderBy ? `${config.orderBy} ${config.orderDir}` : "-- order field ASC/DESC";

    const outputCols = [...selectedFields.map((f) => f.alias), "record_time", "window_start", "window_end"].join(", ");

    return `SELECT ${outputCols}
FROM (
  SELECT *,
    ROW_NUMBER() OVER (
      PARTITION BY ${partitionByCols}
      ORDER BY ${orderByClause}
    ) AS rn
  FROM ${buildWindowTumbleClause(fields, config.windowSize)}
)
WHERE rn <= ${config.n}`;
}

export function generateGenericBoilerplate(fields: InputField[]): string {
    const selected = fields.filter((f) => f.selected);
    if (selected.length === 0) return "";
    return `SELECT *\nFROM (\n${buildInnerSelect(fields)}\n)`;
}

export function generateSql(state: VisualEditorState): string {
    const { inputFields, template } = state;
    switch (template.type) {
        case "generic":
            return generateGenericBoilerplate(inputFields);
        case "cep":
            return generateCepSql(inputFields, template.config);
        case "dedup":
            return generateDedupSql(inputFields, template.config);
        case "windowDedup":
            return generateWindowDedupSql(inputFields, template.config);
        case "windowTopN":
            return generateWindowTopNSql(inputFields, template.config);
    }
}
