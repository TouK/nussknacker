export function rafThrottle<T extends (...args: any) => any>(callback: T): (...args: Parameters<T>) => void {
  let id: number = null
  let lastArgs: Parameters<T>

  const wait = context => () => {
    id = null
    return callback.apply(context, lastArgs)
  }

  return function (...args: Parameters<T>) {
    lastArgs = args
    if (id === null) {
      id = requestAnimationFrame(wait(this))
    }
  }
}
