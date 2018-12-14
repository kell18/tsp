package ru.itclover.tsp.streaming

import scala.reflect.ClassTag
import cats.Semigroup
import ru.itclover.tsp.io.TimeExtractor
import scala.language.higherKinds

// .. type factory
trait Stream {
  type S[_]
  type KeyedS[_, _] <: S[_]
  type TypeInfo[_]
  type Typ
}

trait StreamAlg[S[_], KeyedS[_, _] <: S[_], TypeInfo[_]] {

  def createStream[In, Key, Item](source: Source[In, Key, Item, S]): S[In]

  def keyBy[In, K: TypeInfo](stream: S[In])(partitioner: In => K, maxPartitions: Int): KeyedS[In, K]

  def map[In, Out: TypeInfo, State](stream: S[In])(f: In => Out): S[Out]

  def flatMapWithState[In, State: ClassTag, Out: TypeInfo, K](stream: KeyedS[In, K])(
    mappers: Seq[StatefulFlatMapper[In, State, Out]]
  ): S[Out]

  def reduceNearby[In: Semigroup: TimeExtractor, K](stream: KeyedS[In, K])(getSessionSize: In => Long): S[In]

  def addSink[T](stream: S[T])(sink: Sink[T]): S[T]
}
