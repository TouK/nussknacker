import { drawTextCell, GridCellKind, CustomRenderer } from "@glideapps/glide-data-grid";
import { DatePicker, DatePickerCell, isDatePickerCell } from "./customCells";
import { EditListItem, Item } from "@glideapps/glide-data-grid/src/internal/data-grid/data-grid-types";

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
