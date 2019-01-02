package ru.itclover.tsp.v2
import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import ru.itclover.tsp.v2.Pattern.QI

import scala.annotation.tailrec
import scala.collection.{mutable => m}
import scala.language.higherKinds


/** Couple Pattern */

class CouplePattern[Event, State1 <: PState[T1, State1], State2 <: PState[T2, State2], T1, T2, T3, F[_]: Monad, Cont[_]](
  left: Pattern[Event, T1, State1, F, Cont],
  right: Pattern[Event, T2, State2, F, Cont]
)(
  func: (Result[T1], Result[T2]) => Result[T3]
) extends Pattern[Event, T3, CouplePState[State1, State2, T1, T2, T3], F, Cont] {
  override def apply(
    oldState: CouplePState[State1, State2, T1, T2, T3],
    events: Cont[Event]
  ): F[CouplePState[State1, State2, T1, T2, T3]] = {
    val leftF = left.apply(oldState.left, events)
    val rightF = right.apply(oldState.right, events)
    for (newLeftState  <- leftF;
         newRightState <- rightF) yield {
      // process queues
      val (updatedLeftQueue, updatedRightQueue, newFinalQueue) =
        processQueues(newLeftState.queue, newRightState.queue, oldState.queue)

      CouplePState(
        newLeftState.copyWithQueue(updatedLeftQueue),
        newRightState.copyWithQueue(updatedRightQueue),
        newFinalQueue
      )
    }
  }

  private def processQueues(firstQ: QI[T1], secondQ: QI[T2], totalQ: QI[T3]): (QI[T1], QI[T2], QI[T3]) = {

    @tailrec
    def inner(first: QI[T1], second: QI[T2], total: QI[T3]): (QI[T1], QI[T2], QI[T3]) = {

      def default: (QI[T1], QI[T2], QI[T3]) = (first, second, total)

      (first.headOption, second.headOption) match {
        // if any of parts is empty -> do nothing
        case (_, None)                                                => default
        case (None, _)                                                => default
        case (Some(IdxValue(idx1, val1)), Some(IdxValue(idx2, val2))) =>
          // we emit result only if results on left and right sides come at the same time
          if (idx1 == idx2) {
            val result: Result[T3] = func(val1, val2)
            inner({first.dequeue; first}, {second.dequeue; second}, {total.enqueue(IdxValue(idx1, result)); total})
            // otherwise skip results from one of sides
          } else if (idx1 < idx2) {
            inner({first.dequeue; first}, second, total)
          } else {
            inner(first, {second.dequeue; second}, total)
          }
      }
    }

    inner(firstQ, secondQ, totalQ)
  }

  override def initialState(): CouplePState[State1, State2, T1, T2, T3] =
    CouplePState(left.initialState(), right.initialState(), m.Queue.empty)
}

case class CouplePState[State1 <: PState[T1, State1], State2 <: PState[T2, State2], T1, T2, T3](
  left: State1,
  right: State2,
  override val queue: QI[T3]
) extends PState[T3, CouplePState[State1, State2, T1, T2, T3]] {
  override def copyWithQueue(queue: QI[T3]): CouplePState[State1, State2, T1, T2, T3] = this.copy(queue = queue)
}

case object CouplePState {}