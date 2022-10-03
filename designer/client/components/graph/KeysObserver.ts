import {fromEvents} from "kefir"
import {omitBy} from "lodash"

export interface KeyboardEventLike {
  readonly code: string,
  readonly type: string,
  readonly key: string,
  readonly repeat: boolean,
}

const transform = ({key, code, type, repeat}: KeyboardEvent): KeyboardEventLike => ({key, code, type, repeat})
const keyUpStream = fromEvents(document, `keyup`).map(transform)
const keyDownStream = fromEvents(document, `keydown`).map(transform)
const windowBlur = fromEvents(window, `blur`).map(() => null)
const windowFocus = fromEvents(window, `focus`).map(() => null)

const eventsToObject = (merged: Record<string, KeyboardEventLike>, event: KeyboardEventLike | null) => !event ?
  {} :
  // meta blocks keyup for modified keys so we need manual cleanup
  event.key === "Meta" && event.type === "keyup" ?
    {...omitBy(merged, e => e.key.length === 1)} :
    {...merged, [event.code]: event}

export const pressedKeys = keyDownStream
  .filter(e => !e.repeat)
  .merge(keyUpStream)
  .merge(windowBlur)
  .merge(windowFocus)
  .scan<Record<string, KeyboardEventLike>>(eventsToObject, {})
  .map<KeyboardEventLike[]>(merged => Object.values(merged).filter(event => event.type === "keydown"))
