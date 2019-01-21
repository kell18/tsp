package ru.itclover.tsp.dsl.v2
import cats.{Foldable, Functor, Monad}
import ru.itclover.tsp.core.Window
import ru.itclover.tsp.io.TimeExtractor
import ru.itclover.tsp.v2.Pattern.IdxExtractor
import ru.itclover.tsp.v2._
import ru.itclover.tsp.dsl.v2.FunctionRegistry
import ru.itclover.tsp.v2.aggregators.{AccumPattern, GroupPattern, TimerPattern}

import scala.language.implicitConversions

class ASTPatternGenerator[Event, F[_]: Monad, Cont[_]: Functor: Foldable](
  implicit idxExtractor: IdxExtractor[Event],
  timeExtractor: TimeExtractor[Event]
) {

  val registry = FunctionRegistry.createDefault

  trait AnyState[T] extends PState[T, AnyState[T]]

  implicit def toAnyStatePattern[T](p: Pattern[Event, _, _, F, Cont]): Pattern[Event, T, AnyState[T], F, Cont] =
    p.asInstanceOf[Pattern[Event, T, AnyState[T], F, Cont]]

  def generatePattern[T](ast: AST[T]): Pattern[Event, T, AnyState[T], F, Cont] = {
    ast match {
      case c: Constant[_] => ConstPattern[Event, T, F, Cont](c.value)
      case id: Identifier => new ExtractingPattern[Event, _, _, T, AnyState[T], F, Cont](id.value, id.value) // TODO: Extracting pattern
      case r: Range[_]    => sys.error(s"Range ($r) is valid only in context of a pattern")
      case fc: FunctionCall[_] =>
        fc.arguments.length match {
          case 1 =>
            val p1 = generatePattern(fc.arguments.head)
            new MapPattern(p1)(
              registry.getFunction1(fc.functionName).getOrElse(sys.error(s"Function ${fc.functionName} not found"))
            )
          case 2 =>
            val (p1, p2) = (generatePattern(fc.arguments(0)), generatePattern(fc.arguments(1)))
            new CouplePattern(p1, p2)(
              registry.getFunction2(fc.functionName).getOrElse(sys.error(s"Function ${fc.functionName} not found"))
            )
          case _ => sys.error("Functions with 3 or more arguments not yet supported")
        }
      // TODO: Function registry for reduce
      case ffc: ReducerFunctionCall[_] =>
        val (func, initial) =
          registry.getReducer[T, Any](ffc.functionName).getOrElse(sys.error(s"Reducer function ${ffc.functionName} not found"))
        new ReducePattern(ffc.arguments.map(generatePattern))(func, ffc.cond, Result.succ(initial))
      case psc: PatternStatsCall[_] =>
        psc.functionName match {
          case _ => ???
        }
      // TODO: Function registry for aggregate
      case ac: AggregateCall[_] => ??? //GroupPattern(generatePattern(ac.value), ac.window) // TODO: Which pattern?
      case at: AndThen =>
        AndThenPattern(generatePattern(at.first), generatePattern(at.second))
      // TODO: Window -> TimeInterval in TimerPattern
      case t: Timer => TimerPattern(generatePattern(t.cond), Window(t.interval.min))
      case f: For   => ???
      case a: Assert =>
        new MapPattern(generatePattern[Boolean](a.cond))(
          innerBool => if (innerBool) Result.succ(()) else Result.fail
        )
    }
  }

  def build(sourceCode: String, toleranceFraction: Double
  ): Either[Throwable, Pattern[Event, Any, AnyState[Any], F, Cont]] = {
    val ast = new ASTBuilder(sourceCode, toleranceFraction).start.run()
    ast.toEither.map(generatePattern)
  }
}
