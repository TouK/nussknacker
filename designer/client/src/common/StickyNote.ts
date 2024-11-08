import { LayoutData } from "../types";

export type Dimensions = { width: number; height: number };

export interface StickyNote {
    id?: string;
    noteId: number;
    content: string;
    layoutData: LayoutData;
    dimensions: Dimensions;
    color: string;
    targetEdge?: string;
    editedBy: string;
    editedAt: string;
}
