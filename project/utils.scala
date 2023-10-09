import sbt.{Def, Inc, Result, SettingKey, Task, TaskKey, Value}

object utils {

  sealed abstract class Step[A] {
    def run: Def.Initialize[Task[Result[A]]]
    def map[B](f: A => B): Step[B]
    def flatMap[B](f: A => Step[B]): Step[B]

    final def runThrowing: Def.Initialize[Task[A]] = Def.task {
      run.value match {
        case Inc(cause)   => throw cause
        case Value(value) => value
      }
    }

  }

  object Step {

    def taskUnit: Step[Unit] = task(Def.task(()))

    def deferredTask[A](t: => A): Step[A] =
      task(Def.task(t))

    def task[A](t: Def.Initialize[Task[A]]): Step[A] =
      apply(t.result)

    def taskKey[A](t: TaskKey[A]): Step[A] =
      apply(Def.task(t.result.value))

    def settingKey[A](s: SettingKey[A]): Step[A] =
      apply(Def.task(s.value).result)

    private[this] def apply[A](task: Def.Initialize[Task[Result[A]]]): Step[A] =
      new Step[A] {
        val run = task

        def map[B](f: A => B): Step[B] =
          apply[B](Def.taskDyn {
            run.value match {
              case Inc(inc) => Def.task(Inc(inc): Result[B])
              case Value(a) => Def.task(Value(f(a)))
            }
          })

        def flatMap[B](f: A => Step[B]): Step[B] =
          apply[B](Def.taskDyn {
            run.value match {
              case Inc(inc) => Def.task(Inc(inc): Result[B])
              case Value(a) => Def.task(f(a).run.value)
            }
          })

      }

  }

}
