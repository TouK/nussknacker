export const isInputEvent = (event: Event): boolean => ["INPUT", "SELECT", "TEXTAREA"].includes(event?.target["tagName"])

