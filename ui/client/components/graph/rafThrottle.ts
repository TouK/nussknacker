export function rafThrottle<T extends (...args: any) => any>(callback: T): (...args: Parameters<T>) => void {
  let id = null
  let lastArgs: Parameters<T>

  const wait = context => () => {
    id = null
    return callback.apply(context, lastArgs)
  }

  const throttleFn = requestAnimationFrame || (cb => setTimeout(cb, 20))

  return function (...args: Parameters<T>) {
    lastArgs = args
    if (id === null) {
      id = throttleFn(wait(this))
    }
  }
}
