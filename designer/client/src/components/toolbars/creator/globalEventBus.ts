import { createNanoEvents } from "nanoevents";

export const globalEventBus = createNanoEvents<{
    creatorSearchFocus: () => void;
    createNode: (x: number, y: number) => void;
}>();
