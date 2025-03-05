export const StickyNoteType = "StickyNote";
export function createStickyNoteId(noteId: number) {
    return StickyNoteType + "_" + noteId;
}
