import { LayoutData } from "../types";

export type Dimensions = { width: number; height: number };
export type ColorValueHex = `#${string}`;

export interface StickyNote {
    id?: string;
    noteId: number;
    content: string;
    layoutData: LayoutData;
    dimensions: Dimensions;
    color: ColorValueHex;
    targetEdge?: string;
    editedBy: string;
    editedAt: string;
}
