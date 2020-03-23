import {padStart} from "lodash"

export function debugTime(): number
export function debugTime(start: number, name: string): number
export function debugTime(start?: number, name?: string): number {
  const now = window.performance.now()
  if (start) {
    console.debug(`debugTime: ${padStart((now - start).toFixed(2), 8)} ${name}`)
  }
  return now
}
