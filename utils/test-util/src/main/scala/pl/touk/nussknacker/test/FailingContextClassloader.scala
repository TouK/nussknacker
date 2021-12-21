package pl.touk.nussknacker.test

//We defined null parent, so not class is ever found/loaded by this classloader
class FailingContextClassloader extends ClassLoader(null)