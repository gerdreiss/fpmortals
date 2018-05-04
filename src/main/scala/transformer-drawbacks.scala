// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package transdrawbacks

import scalaz._, Scalaz._
import scalaz.effect.{ IO, LiftIO }

final case class Problem(bad: Int)
final case class Table(last: Int)

trait Lookup[F[_]] {
  def look: F[Int]
}

final class LookupRandom extends Lookup[IO] {
  def look: IO[Int] = IO { util.Random.nextInt }
}

trait MonadErrorState[F[_], E, S] {
  implicit def E: MonadError[F, E]
  implicit def S: MonadState[F, S]
}
object MonadErrorState {
  implicit def create[F[_], E, S](
    implicit E0: MonadError[F, E],
    S0: MonadState[F, S]
  ): MonadErrorState[F, E, S] =
    new MonadErrorState[F, E, S] {
      def E: MonadError[F, E] = E0
      def S: MonadState[F, S] = S0
    }
}

object Logic {
  type Ctx[A] = StateT[EitherT[IO, Problem, ?], Table, A]

  implicit class CtxOps[A](fa: IO[A]) {
    def liftCtx: Ctx[A] =
      fa.liftM[EitherT[?[_], Problem, ?]]
        .liftM[StateT[?[_], Table, ?]]
  }

  type Ctx0[F[_], A] = StateT[EitherT[F, Problem, ?], Table, A]
  type Ctx1[F[_], A] = EitherT[F, Problem, A]
  type Ctx2[F[_], A] = StateT[F, Table, A]
  final class LookupRandomCtx(io: Lookup[IO]) extends Lookup[Ctx] {
    def look1: Ctx[Int] = io.look.liftM[Ctx1].liftM[Ctx2]

    def look2: Ctx[Int] =
      io.look
        .liftM[EitherT[?[_], Problem, ?]]
        .liftM[StateT[?[_], Table, ?]]

    def look3: Ctx[Int] = io.look.liftCtx

    def look: Ctx[Int] = io.look.liftIO[Ctx]
  }

  def foo[F[_]: Monad](L: Lookup[F])(
    implicit E: MonadError[F, Problem],
    S: MonadState[F, Table]
  ): F[Int] = ???

  // implicit Monad, rest explicit. Easier for us, more work for upstream
  def foo1[F[_]: Monad](L: Lookup[F])(
    E: MonadError[F, Problem],
    S: MonadState[F, Table]
  ): F[Int] =
    for {
      old <- S.get
      i   <- L.look
      _ <- if (i === old.last) E.raiseError(Problem(i))
          else ().pure[F]
    } yield i

  // shadow the parameters
  def foo2[F[_]: Monad](L: Lookup[F])(
    implicit
    E: MonadError[F, Problem],
    S: MonadState[F, Table]
  ): F[Int] = shadow(E, S) { (E, S) =>
    for {
      old <- S.get
      i   <- L.look
      _ <- if (i === old.last) E.raiseError(Problem(i))
          else ().pure[F]
    } yield i
  }

  @inline final def shadow[A, B](a: A)(f: A => B): B               = f(a)
  @inline final def shadow[A, B, C](a: A, b: B)(f: (A, B) => C): C = f(a, b)

  // custom Monad
  def foo3[F[_]: Monad](L: Lookup[F])(
    implicit M: MonadErrorState[F, Problem, Table]
  ): F[Int] =
    for {
      old <- M.S.get
      i   <- L.look
      _ <- if (i === old.last) M.E.raiseError(Problem(i))
          else ().pure[F]
    } yield i

  def main(args: Array[String]): Unit = {
    val L: Lookup[Ctx] = new LookupRandomCtx(new LookupRandom)

    foo[Ctx](L)

    foo2[Ctx](L)

    foo3[Ctx](L)
  }

}
