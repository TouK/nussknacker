import { sum, values } from "lodash";
import { useMemo } from "react";

import { DEFAULT_ROW_HEADER_HEIGHT, DEFAULT_TRAILING_ROW_HEIGHT, paddingY } from "./drawText";
import type { TestingDataRecords } from "./types";

export const useTableHeight = (data: TestingDataRecords[], getRowHeight: (rowIndex: number) => number) => {
    const tableHeight = useMemo(() => {
        const rowsHeight: Record<number, number> = {};

        data.forEach((_, rowIndex) => {
            rowsHeight[rowIndex] = getRowHeight(rowIndex);
        });

        const sumOfRowsHeight = sum(values(rowsHeight));
        return sumOfRowsHeight + DEFAULT_ROW_HEADER_HEIGHT + paddingY + DEFAULT_TRAILING_ROW_HEIGHT + paddingY;
    }, [data, getRowHeight]);

    return { tableHeight };
};
