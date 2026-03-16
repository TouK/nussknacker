import type { CustomRenderer, EditListItem, Item } from "@glideapps/glide-data-grid";
import { drawTextCell, GridCellKind } from "@glideapps/glide-data-grid";

import type { DatePickerCell } from "./customCells/DatePicker";
import { DatePicker, isDatePickerCell } from "./customCells/DatePicker";

declare module "@glideapps/glide-data-grid" {
    interface DataEditorProps {
        // Extend the onCellsEdited method
        onCellsEdited?: (newValues: readonly (EditListItem | { location: Item; value: DatePickerCell })[]) => boolean | void;
    }
}

export const customRenderers: CustomRenderer<DatePickerCell>[] = [
    {
        kind: GridCellKind.Custom,
        isMatch: isDatePickerCell,
        draw: (args, cell) => {
            const { date } = cell.data;

            if (date) {
                drawTextCell(args, date, cell.contentAlign);
            }
            return true;
        },
        provideEditor: () => ({
            editor: DatePicker,
            deletedValue: (v) => ({
                ...v,
                copyData: "",
                data: {
                    ...v.data,
                    date: "",
                },
            }),
        }),
    },
];
