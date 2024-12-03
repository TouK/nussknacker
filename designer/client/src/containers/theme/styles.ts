import { Theme } from "@mui/material";
import "react-datetime/css/react-datetime.css";
import { nodeInput, rowAceEditor } from "../../components/graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { blendDarken, blendLighten, getBorderColor } from "./helpers";

const aceEditorStyles = (theme: Theme) => ({
    ".ace-nussknacker .ace_gutter": {
        background: blendLighten(theme.palette.background.paper, 0.1),
        color: theme.palette.text.secondary,
    },
    ".ace-nussknacker.ace_editor": {
        fontFamily: "'Roboto Mono', 'Monaco', monospace",
    },
    ".ace-nussknacker .ace_print-margin": {
        width: "1px",
        background: "red",
    },

    ".ace-nussknacker .ace_search": {
        background: "#818181",
    },

    ".ace-nussknacker .ace_search_field": {
        color: "#ccc",
    },

    ".ace-nussknacker": {
        backgroundColor: theme.palette.background.paper,
        color: theme.palette.text.secondary,
    },
    ".ace-nussknacker .ace_cursor": {
        color: "#F8F8F0",
    },
    ".ace-nussknacker .ace_marker-layer .ace_selection": {
        background: "#49483E",
    },
    ".ace-nussknacker.ace_multiselect .ace_selection.ace_start": {
        boxShadow: "0 0 3px 0px #333",
    },
    ".ace-nussknacker .ace_marker-layer .ace_step": {
        background: "rgb(102, 82, 0)",
    },
    ".ace-nussknacker .ace_marker-layer .ace_bracket": {
        margin: 0,
        border: "1px solid #FFC66D",
    },
    ".ace-nussknacker .ace_marker-layer .ace_active-line": {
        background: blendDarken(theme.palette.background.paper, 0.2),
    },
    ".ace-nussknacker .ace_gutter-active-line": {
        backgroundColor: blendDarken(theme.palette.background.paper, 0.2),
    },
    ".ace-nussknacker .ace_marker-layer .ace_selected-word": {
        border: "1px solid #49483E",
    },
    ".ace-nussknacker .ace_invisible": {
        color: "#52524d",
    },
    ".ace-nussknacker .ace_entity.ace_name.ace_tag, .ace-nussknacker .ace_keyword, .ace-nussknacker .ace_meta.ace_tag, .ace-nussknacker .ace_storage":
        {
            color: "#db7e3a",
        },
    ".ace-nussknacker .ace_punctuation, .ace-nussknacker .ace_punctuation.ace_tag": {
        color: "#fff",
    },
    ".ace-nussknacker .ace_constant.ace_character, .ace-nussknacker .ace_constant.ace_language, .ace-nussknacker .ace_constant.ace_numeric, .ace-nussknacker .ace_constant.ace_other":
        {
            color: "#AE81FF",
        },
    ".ace-nussknacker .ace_invalid": {
        color: "#F8F8F0",
        backgroundColor: "#F92672",
    },
    ".ace-nussknacker .ace_invalid.ace_deprecated": {
        color: "#F8F8F0",
        backgroundColor: "#AE81FF",
    },
    ".ace-nussknacker .ace_support.ace_constant, .ace-nussknacker .ace_support.ace_function": {
        color: "#f3b560",
    },
    ".ace-nussknacker .ace_fold": {
        backgroundColor: "#A6E22E",
        borderColor: "#F8F8F2",
    },
    ".ace-nussknacker .ace_storage.ace_type, .ace-nussknacker .ace_support.ace_class, .ace-nussknacker .ace_support.ace_type": {
        fontStyle: "italic",
        color: "#f3b560",
    },
    ".ace-nussknacker .ace_entity.ace_name.ace_function, .ace-nussknacker .ace_entity.ace_other, .ace-nussknacker .ace_entity.ace_other.ace_attribute-name, .ace-nussknacker .ace_variable":
        {
            color: "#9876AA",
        },
    ".ace-nussknacker .ace_variable.ace_parameter": {
        fontStyle: "italic",
        color: "#FD971F",
    },
    ".ace-nussknacker .ace_string": {
        color: "#6A8759",
    },
    ".ace-nussknacker .ace_comment": {
        color: "#75715E",
    },
    ".ace-nussknacker .ace_spel": {
        color: "#337AB7",
    },
    ".ace-nussknacker .ace_paren": {
        fontWeight: "bold",
    },
    ".ace-nussknacker .ace_alias": {
        color: "#37CB86",
    },
    ".ace-nussknacker .ace_indent-guide": {
        background:
            "url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAACCAYAAACZgbYnAAAAEklEQVQImWPQ0FD0ZXBzd/wPAAjVAoxeSgNeAAAAAElFTkSuQmCC) right repeat-y",
    },
    ".ace_hidden-cursors .ace_cursor": {
        opacity: 0,
    },
    ".ace_editor.ace_autocomplete": {
        width: "400px",
    },
    /* Without those settings below, type (meta) shade method/variable name (value)*/
    ".ace_autocomplete .ace_line > *": {
        flex: "0 0 auto",
    },
    ".ace_autocomplete .ace_line .ace_": {
        flex: "0 0 auto",
        overflow: "auto",
        whiteSpace: "pre",
    },
    ".ace_defaultMethod, .ace_defaultMethod + .ace_completion-meta": {
        color: "#ffe1b9",
    },
    ".ace_classMethod, .ace_classMethod + .ace_completion-meta": {
        color: "#708687",
    },
    ".ace_tooltip.ace_doc-tooltip": {
        fontSize: "0.7em",
        ".function-docs": {
            whiteSpace: "pre-wrap",
            "> hr": {
                marginTop: 0,
                marginBottom: 0,
            },

            "& p": {
                marginTop: "5px",
                marginBottom: "5px",
            },
        },
    },
});

const DTPickerStyles = (theme: Theme) => ({
    ".rdtOpen .rdtPicker": {
        position: "fixed",
        backgroundColor: "#222222",
        borderColor: getBorderColor(theme),
    },

    ".rdtPicker tfoot, .rdtPicker th": {
        borderColor: getBorderColor(theme),
        paddingTop: "0.25em",
        paddingBottom: "0.25em",
    },

    ".rdtPicker td.rdtActive, .rdtPicker td.rdtActive:hover": {
        backgroundColor: "#db7e3a",
        color: theme.palette.common.black,
    },

    ".rdtPicker thead tr:first-of-type th:hover": {
        backgroundColor: "inherit",
        color: "#db7e3a",
    },

    ".rdtCounter .rdtBtn:hover, td.rdtMonth:hover, td.rdtYear:hover, .rdtPicker td.rdtDay:hover, .rdtPicker td.rdtHour:hover, .rdtPicker td.rdtMinute:hover, .rdtPicker td.rdtSecond:hover, .rdtPicker .rdtTimeToggle:hover":
        {
            backgroundColor: theme.palette.common.black,
            color: "#db7e3a",
        },

    ".rdtPicker td.rdtToday:before": {
        borderBottomColor: "#db7e3a",
    },

    ".rdtPicker td.rdtActive.rdtToday:before": {
        borderBottomColor: theme.palette.common.black,
    },
});

export const fontFamily = [
    "Inter",
    "-apple-system",
    "BlinkMacSystemFont",
    '"Segoe UI"',
    '"Helvetica Neue"',
    "Arial",
    "sans-serif",
    '"Apple Color Emoji"',
    '"Segoe UI Emoji"',
    '"Segoe UI Symbol"',
].join(",");

export const globalStyles = (theme: Theme) => ({
    "html, body": {
        margin: 0,
        padding: 0,
        height: "100dvh",
        color: theme.palette.text.primary,
        fontSize: "16px",
        overflow: "hidden",
        letterSpacing: "unset",
        WebkitFontSmoothing: "initial",
        lineHeight: 1.428571429,
        fontFamily,
    },
    [`.${nodeInput}, button, .${rowAceEditor}`]: {
        fontFamily: "inherit",
        fontSize: "inherit",
        lineHeight: "inherit",
        boxShadow: "none",
        border: "none",
        backgroundColor: theme.palette.background.paper,
        outline: `1px solid ${getBorderColor(theme)}`,
        "&:focus, &:focus-within": {
            outline: `1px solid ${theme.palette.primary.main}`,
        },
    },
    button: {
        ...theme.typography.button,
        textTransform: "none",
        outline: `1px solid ${getBorderColor(theme)}`,
        ":hover": {
            cursor: "pointer",
            backgroundColor: theme.palette.action.hover,
        },
    },
    [`.${nodeInput}[readonly], .${nodeInput}[type='checkbox'][readonly]:after, .${nodeInput}[type='radio'][readonly]:after, .${rowAceEditor}.read-only, .${rowAceEditor} .read-only .ace_scroller, .${rowAceEditor}.read-only .ace_content`]:
        {
            backgroundColor: `${theme.palette.action.disabledBackground} !important`,
            color: `${theme.palette.action.disabled} !important`,
        },
    [`input[disabled], select[disabled], textarea[disabled], input[type='checkbox'][disabled]:after, input[type='radio'][disabled]:after, .${rowAceEditor}[disabled]`]:
        {
            backgroundColor: `${theme.palette.action.disabledBackground} !important`,
            color: `${theme.palette.action.disabled} !important`,
        },
    " button,input,optgroup,select,textarea": {
        color: "inherit",
        font: "inherit",
        margin: 0,
    },

    a: {
        textDecoration: "none",
        ":hover": {
            textDecoration: "underline",
        },
    },
    ".hide": {
        display: "none",
    },
    ".details": {
        display: "inline-block",
    },
    ".modalContentDark": {
        "& h3": {
            fontSize: "1.3em",
        },
    },
    ".services": {
        height: "100%",
        overflowY: "auto",
    },

    ".notifications-wrapper": {
        position: "absolute",
        bottom: "25px",
        right: "25px",
        zIndex: 10000,
    },
    ".notification-dismiss": {
        display: "none",
    },

    ".gdg-style": {
        // Without it, date picker is not visible.
        // There is no option to overwrite this property via glide-data-grid customEditors provideEditor styleOverwrites
        "&:has(.rdtPicker)": {
            transform: "none !important",
        },
    },
    ...aceEditorStyles(theme),
    ...DTPickerStyles(theme),
});

export const formLabelWidth = "20%";
