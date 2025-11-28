import type { FixedValuesOption } from "../../fragment-input-definition/item/types";
import type { Prettify } from "../../useNodeTypeDetailsContentLogic";
import type { TimeRange } from "./Duration/TimeRangeComponent";
import type { EditorType } from "./types";

type PureEditorConfig = {
    type:
        | EditorType.SPEL_PARAMETER_EDITOR
        | EditorType.BOOL_PARAMETER_EDITOR
        | EditorType.STATIC_STRING_PARAMETER_EDITOR
        | EditorType.DATE
        | EditorType.TIME
        | EditorType.DATE_TIME
        | EditorType.CRON_EDITOR
        | EditorType.TEXTAREA_PARAMETER_EDITOR
        | EditorType.JSON_PARAMETER_EDITOR
        | EditorType.SQL_PARAMETER_EDITOR
        | EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR
        | EditorType.TABLE_EDITOR
        | EditorType.JSON_TEMPLATE_PARAMETER_EDITOR;
};

type ExtendedEditorConfig =
    | {
          type:
              | EditorType.FIXED_VALUES_PARAMETER_EDITOR
              | EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR
              | EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR;
          possibleValues: FixedValuesOption[];
      }
    | {
          type: EditorType.DURATION_EDITOR | EditorType.PERIOD_EDITOR;
          timeRangeComponents: TimeRange[];
      }
    | {
          type: EditorType.DICT_PARAMETER_EDITOR;
          dictId: string;
      };

export type EditorConfig = PureEditorConfig | ExtendedEditorConfig;

export type EditorConfigForType<T extends EditorType> = Prettify<EditorConfig & { type: T }>;
