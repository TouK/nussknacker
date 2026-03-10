import AddIcon from "@mui/icons-material/Add";
import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import SearchIcon from "@mui/icons-material/Search";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import { alpha, Box, Button, Chip, Collapse, IconButton, InputAdornment, styled, TextField, Tooltip, Typography } from "@mui/material";
import React from "react";

import type { VariableTypes } from "../../types/validation";
import { ContextTreeNode } from "../builderComponents/ContextTreeNode";
import { PanelHeader, PanelPaper, ScrollArea, SpelOutput } from "../builderComponents/panelStyles";
import { treeNodeMatchesFilter } from "../builderComponents/typeUtils";
import type { ContextData, TopicEntry } from "./dataMapperUtils";
import { FieldRow } from "./FieldRow";
import { SampleJsonPanel } from "./SampleJsonPanel";
import { TopicPickerPanel } from "./TopicPickerPanel";
import { useDataMapper } from "./useDataMapper";

// ─── Styled ───────────────────────────────────────────────────────────────────

const RootBox = styled(Box, { shouldForwardProp: (p) => p !== "embedded" })<{ embedded?: boolean }>(({ theme, embedded }) => ({
    display: "flex",
    flexDirection: "column",
    gap: 0,
    padding: 0,
    ...(embedded
        ? { flex: 1, minHeight: 0, overflow: "hidden", height: "100%" }
        : { minHeight: "100vh", gap: theme.spacing(2), padding: theme.spacing(2.5) }),
    backgroundColor: theme.palette.background.default,
    fontFamily: theme.typography.fontFamily,
}));

// ─── Re-exports for consumers ─────────────────────────────────────────────────

export type { ContextData, TopicEntry };

// ─── Props ────────────────────────────────────────────────────────────────────

interface DataMapperProps {
    onInsert?: (spel: string) => void;
    initialContext?: ContextData;
    initialExpression?: string;
    variableTypes?: VariableTypes;
    /** Override the default topic fetching (which uses a generic kafka sink probe). */
    fetchTopicDefinitions?: () => Promise<TopicEntry[]>;
}

// ─── DataMapper ───────────────────────────────────────────────────────────────

export function DataMapper({
    onInsert,
    initialContext,
    initialExpression,
    variableTypes,
    fetchTopicDefinitions: fetchTopicDefinitionsOverride,
}: DataMapperProps = {}): React.JSX.Element {
    const dm = useDataMapper({
        initialContext,
        initialExpression,
        variableTypes,
        isEmbedded: !!onInsert,
        fetchTopicDefinitionsOverride,
    });

    return (
        <RootBox embedded={!!onInsert}>
            {/* Header */}
            {!onInsert && (
                <Box sx={{ flexShrink: 0, px: 2.5, pt: 2.5 }}>
                    <Typography variant="h5" sx={{ fontWeight: 600, lineHeight: 1.3 }}>
                        Data Mapper
                    </Typography>
                    <Typography variant="caption" sx={{ color: "text.secondary" }}>
                        Map context variables to a target record
                    </Typography>
                </Box>
            )}

            {/* Scrollable content */}
            <Box
                sx={
                    onInsert
                        ? { flex: 1, overflowY: "auto", minHeight: 0, display: "flex", flexDirection: "column", gap: 2, p: 2.5 }
                        : { display: "contents" }
                }
            >
                <Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
                    {/* Left: Context Variables */}
                    <PanelPaper sx={{ width: 360, flexShrink: 0 }}>
                        <PanelHeader>
                            <Box>
                                <Typography sx={{ fontSize: 13, fontWeight: 600 }}>Context Variables</Typography>
                                <Typography sx={{ fontSize: 11, color: "text.secondary" }}>Drag leaf nodes onto target fields</Typography>
                            </Box>
                            <Tooltip title="Load context from JSON sample">
                                <IconButton
                                    size="small"
                                    onClick={() => dm.setShowContextSample((v) => !v)}
                                    color={dm.showContextSample ? "primary" : "default"}
                                >
                                    <UploadFileIcon sx={{ fontSize: 16 }} />
                                </IconButton>
                            </Tooltip>
                        </PanelHeader>
                        <Collapse in={dm.showContextSample}>
                            <SampleJsonPanel
                                title="Paste your full context JSON (top-level keys = variable names):"
                                placeholder={'{\n  "input": { "id": 1, "name": "foo" },\n  "http_output": { "statusCode": 200 }\n}'}
                                onApply={dm.applyContextSample}
                                onClose={() => dm.setShowContextSample(false)}
                            />
                        </Collapse>
                        <Box sx={{ px: 1, pt: 1, pb: 0.5 }}>
                            <TextField
                                size="small"
                                placeholder="Search…"
                                value={dm.contextFilter}
                                onChange={(e) => dm.setContextFilter(e.target.value)}
                                fullWidth
                                InputProps={{
                                    startAdornment: (
                                        <InputAdornment position="start">
                                            <SearchIcon sx={{ fontSize: 16, color: "text.disabled" }} />
                                        </InputAdornment>
                                    ),
                                }}
                                sx={{ "& .MuiInputBase-input": { fontSize: 12, py: "4px" } }}
                            />
                        </Box>
                        <ScrollArea sx={{ p: 1, pt: 0.5 }}>
                            {Object.entries(dm.enrichedContext)
                                .filter(
                                    ([key, val]) => !dm.contextFilter || treeNodeMatchesFilter(key, val, dm.contextFilter.toLowerCase()),
                                )
                                .map(([key, val]) => (
                                    <ContextTreeNode
                                        key={key}
                                        name={`#${key}`}
                                        value={val}
                                        path={key}
                                        depth={0}
                                        onSelect={dm.onTreeSelect}
                                        selectedPath={dm.selPath}
                                        filterText={dm.contextFilter}
                                        nullSafeDrag
                                    />
                                ))}
                        </ScrollArea>
                    </PanelPaper>

                    {/* Right: Target Record + Expression */}
                    <Box sx={{ flex: 1, display: "flex", flexDirection: "column", gap: 2 }}>
                        <PanelPaper sx={{ backgroundColor: "transparent" }}>
                            <PanelHeader>
                                <Box>
                                    <Typography sx={{ fontSize: 13, fontWeight: 600 }}>Target Record</Typography>
                                    <Chip
                                        label={`${dm.mappedCount} / ${dm.fields.length} mapped`}
                                        size="small"
                                        variant="outlined"
                                        sx={{ height: 16, fontSize: 10, "& .MuiChip-label": { px: "5px" } }}
                                    />
                                </Box>
                                <Box sx={{ display: "flex", gap: 0.75 }}>
                                    <Tooltip title="Auto-fill unmapped fields by matching field names to context variables">
                                        <Button
                                            startIcon={<AutoFixHighIcon />}
                                            size="small"
                                            variant="outlined"
                                            onClick={dm.handleAutoMap}
                                            sx={{
                                                fontSize: 12,
                                                textTransform: "none",
                                                borderColor: "divider",
                                                color: "text.primary",
                                                "&:hover": { borderColor: "text.secondary" },
                                            }}
                                        >
                                            Auto-map
                                        </Button>
                                    </Tooltip>
                                    <Button
                                        startIcon={<AddIcon />}
                                        size="small"
                                        variant="outlined"
                                        onClick={dm.addField}
                                        sx={{ fontSize: 12, textTransform: "none" }}
                                    >
                                        Add Field
                                    </Button>
                                    <Button
                                        size="small"
                                        variant={dm.showTopicPicker ? "contained" : "outlined"}
                                        color={dm.showTopicPicker ? "primary" : "inherit"}
                                        onClick={() => (dm.showTopicPicker ? dm.setShowTopicPicker(false) : dm.handleOpenTopicPicker())}
                                        sx={{ fontSize: 12, textTransform: "none" }}
                                    >
                                        From Topic
                                    </Button>
                                    <Button
                                        startIcon={<UploadFileIcon />}
                                        size="small"
                                        variant={dm.showTargetSample ? "contained" : "outlined"}
                                        color={dm.showTargetSample ? "secondary" : "inherit"}
                                        onClick={() => {
                                            dm.setShowTargetSample((v) => !v);
                                            dm.setShowTopicPicker(false);
                                        }}
                                        sx={{ fontSize: 12, textTransform: "none" }}
                                    >
                                        From Sample
                                    </Button>
                                </Box>
                            </PanelHeader>
                            <Collapse in={dm.showTargetSample}>
                                <SampleJsonPanel
                                    title="Paste a JSON object to auto-detect output fields and types:"
                                    placeholder={
                                        '{\n  "icao24": "4952c9",\n  "callsign": "LOT672",\n  "on_ground": true,\n  "velocity": 5.14\n}'
                                    }
                                    mergeLabel="Merge (add missing)"
                                    onApply={dm.applyTargetSample}
                                    onClose={() => dm.setShowTargetSample(false)}
                                />
                            </Collapse>
                            <Collapse in={dm.showTopicPicker}>
                                <TopicPickerPanel
                                    loading={dm.topicsLoading}
                                    entries={dm.topicEntries}
                                    onSelect={dm.applyTopicSchema}
                                    onClose={() => dm.setShowTopicPicker(false)}
                                />
                            </Collapse>
                            <ScrollArea sx={{ p: 1 }}>
                                {dm.fields.map((f) => (
                                    <FieldRow
                                        key={f.id}
                                        field={f}
                                        depth={0}
                                        selFieldId={dm.selField}
                                        dragOverFieldId={dm.dragOverId}
                                        variableTypes={variableTypes ?? {}}
                                        fieldErrors={dm.fieldErrors}
                                        onSelectId={(id) => dm.setSelField(dm.selField === id ? null : id)}
                                        onDragOverId={dm.setDragOverId}
                                        onChange={dm.updateField}
                                        onAddChild={dm.addChildField}
                                        onRemove={dm.removeField}
                                        onMove={dm.moveField}
                                        onDrop={dm.onDrop}
                                        onValidateExpression={dm.validateExpression}
                                    />
                                ))}
                                {/* Drop zone */}
                                <Box
                                    onDragOver={(e) => {
                                        e.preventDefault();
                                        dm.setDropZoneActive(true);
                                    }}
                                    onDragLeave={() => dm.setDropZoneActive(false)}
                                    onDrop={(e) => {
                                        e.preventDefault();
                                        const path = e.dataTransfer.getData("text/plain");
                                        if (path) dm.addFieldFromDrop(path);
                                        else dm.setDropZoneActive(false);
                                    }}
                                    sx={(theme) => ({
                                        mt: dm.fields.length > 0 ? 0.5 : 0,
                                        minHeight: dm.fields.length === 0 ? 120 : 40,
                                        borderRadius: 1,
                                        border: `2px dashed ${dm.dropZoneActive ? theme.palette.primary.main : "transparent"}`,
                                        backgroundColor: dm.dropZoneActive ? alpha(theme.palette.primary.main, 0.06) : "transparent",
                                        transition: "border-color 0.15s, background-color 0.15s",
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "center",
                                    })}
                                >
                                    {dm.fields.length === 0 && !dm.dropZoneActive && (
                                        <Box sx={{ textAlign: "center", color: "text.disabled" }}>
                                            <Typography sx={{ fontSize: 12 }}>No output fields yet.</Typography>
                                            <Typography sx={{ fontSize: 11 }}>
                                                Drag a variable here or use <strong>Add Field</strong> above.
                                            </Typography>
                                        </Box>
                                    )}
                                    {dm.dropZoneActive && (
                                        <Typography sx={{ fontSize: 11, color: "primary.main" }}>Drop to add field</Typography>
                                    )}
                                </Box>
                            </ScrollArea>
                        </PanelPaper>

                        {/* Expression output */}
                        <PanelPaper
                            sx={{ opacity: 0.65, "&:hover": { opacity: 1 }, transition: "opacity 0.2s", backgroundColor: "transparent" }}
                        >
                            <PanelHeader>
                                <Typography sx={{ fontSize: 12, fontWeight: 500, color: "text.secondary" }}>Expression</Typography>
                                <Tooltip title="Copy to clipboard">
                                    <IconButton size="small" onClick={() => navigator.clipboard?.writeText(dm.spelOutput())}>
                                        <ContentCopyIcon sx={{ fontSize: 16 }} />
                                    </IconButton>
                                </Tooltip>
                            </PanelHeader>
                            <SpelOutput>{dm.spelOutput()}</SpelOutput>
                        </PanelPaper>
                    </Box>
                </Box>
            </Box>

            {/* Footer */}
            <Box
                sx={(theme) => ({
                    flexShrink: 0,
                    display: "flex",
                    alignItems: "center",
                    gap: 3,
                    px: 2.5,
                    py: 1,
                    borderTop: `1px solid ${theme.palette.divider}`,
                    backgroundColor: alpha(theme.palette.background.paper, 0.6),
                })}
            >
                <Typography variant="caption" color="text.secondary">
                    Drag source variable → target field to map
                </Typography>
                <Typography variant="caption" color="text.secondary">
                    Click field to edit name, type, or write an expression with autocomplete
                </Typography>
                <Box sx={{ flex: 1 }} />
                {onInsert && (
                    <Button
                        variant="contained"
                        color="primary"
                        size="small"
                        onClick={() => onInsert(dm.spelOutput())}
                        sx={{ textTransform: "none" }}
                    >
                        Apply
                    </Button>
                )}
            </Box>
        </RootBox>
    );
}

export default DataMapper;
