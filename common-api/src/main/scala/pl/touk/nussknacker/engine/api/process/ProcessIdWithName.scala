package pl.touk.nussknacker.engine.api.process

// This class is useful in places where someone needs to access scenario by id in db but also wants to use a human-friendly
// identifier e.g. to produce an error
// It won't be necessary when we remove ProcessId (see TODOs there)
final case class ProcessIdWithName(id: ProcessId, name: ProcessName)
