package hotpotato

import hotpotato.Examples._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import shapeless._
import shapeless.syntax.inject._
import cats.implicits._
import hotpotato.util.AssertType
import org.scalatest.Inside
import zio.Exit

class ErrorTransformSpec extends AnyWordSpec with Matchers with Inside {
  implicit val embedder: Embedder[E123] = Embedder.make

  "mapErrorAllInto should handle all coproduct cases into a single type" in {
    // Exhaustive error handling
    def exec(either: Either[E123, Unit]): Either[String, Unit] =
      either.mapErrorAllInto(
        (_: E1) => "e1",
        (_: E2) => "e2",
        (_: E3) => "e3",
      )

    exec(Left(Coproduct[E123](E1()))) shouldBe Left("e1")
    exec(Left(Coproduct[E123](E2()))) shouldBe Left("e2")
    exec(Left(Coproduct[E123](E3()))) shouldBe Left("e3")

  }

  "mapErrorSome" should {

    "handle some cases (each into a different type), with the result type being the unique combination of all types + the unhandled cases" in {
      type ResultError = String :+: Int :+: E1 :+: CNil

      def exec(either: Either[E123, Unit]): Either[ResultError, Unit] =
        either.mapErrorSome(
          (_: E3) => "e3",
          (_: E2) => 0,
        )

      exec(Left(Coproduct[E123](E1()))) shouldBe Left(Coproduct[ResultError](E1()))
      exec(Left(Coproduct[E123](E2()))) shouldBe Left(Coproduct[ResultError](0))
      exec(Left(Coproduct[E123](E3()))) shouldBe Left(Coproduct[ResultError]("e3"))
    }

    "handle some cases (each into a different type), with deduplication of output types" in {
      type ResultError = String :+: Int :+: E1 :+: CNil

      def exec(either: Either[E1_E2_E3_E4, Unit]): Either[String :+: Int :+: E1 :+: CNil, Unit] =
        either.mapErrorSome(
          (_: E3) => "e3",
          (_: E2) => "e2",
          (_: E4) => 0,
        )

      exec(Left(Coproduct[E1_E2_E3_E4](E1()))) shouldBe Left(Coproduct[ResultError](E1()))
      exec(Left(Coproduct[E1_E2_E3_E4](E2()))) shouldBe Left(Coproduct[ResultError]("e2"))
      exec(Left(Coproduct[E1_E2_E3_E4](E3()))) shouldBe Left(Coproduct[ResultError]("e3"))
      exec(Left(Coproduct[E1_E2_E3_E4](E4()))) shouldBe Left(Coproduct[ResultError](0))
    }

    "does not modify the success result" in {
      import PureExamples._
      AssertType(
        g_E123.mapErrorSome(
          (_: E1) => "1",
          (_: E2) => 2,
        ),
      ).is[Either[String :+: Int :+: E3 :+: CNil, String]] shouldBe Right("")
    }

  }

  "mapErrorAllInto" should {
    import PureExamples._

    "map a single error into one type" in {
      (Left(e1.inject): Either[E1 :+: CNil, String]).mapErrorAllInto(
        (_: E1) => "e1",
      ) shouldBe Left("e1")
    }

    "map error all into one type" in {

      def exec(either: Either[E123, String]) =
        either.mapErrorAllInto(
          (_: E1) => "e1",
          (_: E2) => "e2",
          (_: E3) => "e3",
        )
      exec(b_E123_1) shouldBe Left("e1")
      exec(b_E123_2) shouldBe Left("e2")
      exec(b_E123_3) shouldBe Left("e3")
    }

    "does not modify the success result" in {
      g_E123.mapErrorAllInto(
        (_: E1) => 1,
        (_: E2) => 2,
        (_: E3) => 3,
      ) shouldBe Right("")
    }

  }

  "mapErrorAll" should {
    import PureExamples._
    "Allow each error type to map to a different error type, with the result being the unique combination of them" in {
      type ExpectedOut = String :+: Int :+: Right[Nothing, Int] :+: CNil
      def exec(either: Either[E1234, String]): Either[ExpectedOut, String] =
        either.mapErrorAll(
          (_: E1) => "e1",
          (_: E2) => 2,
          (_: E3) => 3,
          (_: E4) => Right(1),
        )

      exec(b_E1234_1) shouldBe Left("e1".inject[ExpectedOut])
      exec(b_E1234_2) shouldBe Left(2.inject[ExpectedOut])
      exec(b_E1234_3) shouldBe Left(3.inject[ExpectedOut])
      exec(b_E1234_4) shouldBe Left(Right(1).inject[ExpectedOut])
    }

    "mapping a single error" in {
      (Left(e1.inject): Either[E1 :+: CNil, String]).mapErrorAll(
        (_: E1) => "e1",
      ) shouldBe Left("e1".inject[String :+: CNil])
    }

    "does not modify the success result" in {
      g_E123.mapErrorAll(
        (_: E1) => "e1",
        (_: E2) => 2,
        (_: E3) => 3,
      ) shouldBe Right("")
    }
  }

  "flatMapErrorAllInto" should {
    import PureExamples._
    "use the successful result if the recovery succeeds" in {
      def exec(either: Either[E123, String]): Either[Unit, String] =
        AssertType {
          either.flatMapErrorAllInto(
            (_: E1) => "1".asRight[Unit],
            (_: E2) => "2".asRight[Unit],
            (_: E3) => "3".asRight[Unit],
          )
        }.is[Either[Unit, String]]

      exec(b_E123_1) shouldBe Right("1")
      exec(b_E123_2) shouldBe Right("2")
      exec(b_E123_3) shouldBe Right("3")
    }

    "use error returned from recovery effect if failed" in {
      def exec(either: Either[E123, String]): Either[Int, String] =
        AssertType {
          either.flatMapErrorAllInto(
            (_: E1) => 1.asLeft[String],
            (_: E2) => 2.asLeft[String],
            (_: E3) => 3.asLeft[String],
          )
        }.is[Either[Int, String]]

      exec(b_E123_1) shouldBe Left(1)
      exec(b_E123_2) shouldBe Left(2)
      exec(b_E123_3) shouldBe Left(3)
    }

    "does not modify the success result" in {
      g_E123.flatMapErrorAllInto(
        (_: E1) => 1.asLeft[String],
        (_: E2) => 2.asLeft[String],
        (_: E3) => 3.asLeft[String],
      ) shouldBe Right("")
    }

  }

  "flatMapErrorAll" should {
    import PureExamples._
    "Allow each error type to map to a different error type, with the result error being the unique combination of them" in {
      type ExpectedError = String :+: Int :+: Boolean :+: CNil
      def exec(either: Either[E123, String]): Either[ExpectedError, String] =
        AssertType {
          either.flatMapErrorAll(
            (_: E1) => "1".asLeft[String],
            (_: E2) => 2.asLeft[String],
            (_: E3) => Left(true),
          )
        }.is[Either[ExpectedError, String]]

      exec(b_E123_1) shouldBe Left("1".inject[ExpectedError])
      exec(b_E123_2) shouldBe Left(2.inject[ExpectedError])
      exec(b_E123_3) shouldBe Left(true.inject[ExpectedError])
    }

    "use error returned from recovery effect if failed" in {
      type ExpectedError = String :+: Int :+: Boolean :+: CNil
      def exec(either: Either[E123, String]) =
        AssertType {
          either.flatMapErrorAll(
            (_: E1) => "1".asLeft[String],
            (_: E2) => 2.asLeft[String],
            (_: E3) => true.asLeft[String],
          )
        }.is[Either[ExpectedError, String]]

      exec(b_E123_1) shouldBe Left("1".inject[ExpectedError])
      exec(b_E123_2) shouldBe Left(2.inject[ExpectedError])
      exec(b_E123_3) shouldBe Left(true.inject[ExpectedError])
    }

    "does not modify the success result" in {
      g_E123.flatMapErrorAll(
        (_: E1) => "1".asLeft[String],
        (_: E2) => 2.asLeft[String],
        (_: E3) => Left(true),
      ) shouldBe Right("")
    }
  }

  "flatMapErrorSome" should {
    import PureExamples._

    "Allow each error type to map to a different error type, with the result error being the unique combination of them" in {
      type ExpectedError = String :+: Int :+: E3 :+: CNil
      def exec(either: Either[E123, String]): Either[ExpectedError, String] =
        AssertType {
          either.flatMapErrorSome(
            (_: E1) => "1".asLeft[String],
            (_: E2) => 2.asLeft[String],
          )
        }.is[Either[ExpectedError, String]]

      exec(b_E123_1) shouldBe Left("1".inject[ExpectedError])
      exec(b_E123_2) shouldBe Left(2.inject[ExpectedError])
      exec(b_E123_3) shouldBe Left(e3.inject[ExpectedError])
    }

    "use error returned from recovery effect if failed" in {
      type ExpectedError = String :+: Int :+: E3 :+: CNil
      def exec(either: Either[E123, String]) =
        AssertType {
          either.flatMapErrorSome(
            (_: E1) => "1".asLeft[String],
            (_: E2) => 2.asLeft[String],
          )
        }.is[Either[ExpectedError, String]]

      exec(b_E123_1) shouldBe Left("1".inject[ExpectedError])
      exec(b_E123_2) shouldBe Left(2.inject[ExpectedError])
      exec(b_E123_3) shouldBe Left(e3.inject[ExpectedError])
    }

    "does not modify the success result" in {
      g_E123.flatMapErrorSome(
        (_: E1) => "1".asLeft[String],
        (_: E2) => 2.asLeft[String],
      ) shouldBe Right("")
    }
  }

  "unifyError" should {
    "unify the error into one" in {
      import hotpotato.PureExamples._
      inside(b_E12_1.unifyError) {
        case Left(E1()) => succeed
      }
    }
  }

  "dieIf" should {
    "dieIf1 throws the provided case, and ends with Nothing as the error" in {
      import ZioExamples._
      (unsafeRun(zio.IO.fail(E1().inject[E1 :+: CNil]).dieIf1[E1]): Exit[Nothing, String]) shouldBe
        Exit.die(e1)
    }

    "throws the case as the exception (one type param)" in {
      import ZioExamples._

      (unsafeRun(b_E1234_1.dieIf[E1]): Exit[E234, String]) shouldBe Exit.die(e1)

      (unsafeRun(b_E1234_2.dieIf[E1]): Exit[E234, String]) shouldBe Exit.fail(e2.inject[E234])
      (unsafeRun(b_E1234_3.dieIf[E1]): Exit[E234, String]) shouldBe Exit.fail(e3.inject[E234])
    }

    "throws all selected types when selected" in {
      import ZioExamples._

      (unsafeRun(b_E1234_1.dieIf[E1, E3]): Exit[E24, String]) shouldBe Exit.die(e1)

      (unsafeRun(b_E1234_2.dieIf[E1, E3]): Exit[E24, String]) shouldBe Exit.fail(e2.inject[E24])

      (unsafeRun(b_E1234_3.dieIf[E1, E3]): Exit[E24, String]) shouldBe Exit.die(e3)

      (unsafeRun(b_E1234_4.dieIf[E1, E3]): Exit[E24, String]) shouldBe Exit.fail(e4.inject[E24])
    }
  }

  "errorAsCoproduct" should {
    "transform error sealed traits into coproducts" in {
      type ExpectedError = Child1 :+: Child2 :+: Child3 :+: CNil
      def exec(either: Either[Sealed, String]) =
        AssertType {
          either.errorAsCoproduct
        }.is[Either[ExpectedError, String]]

      exec(Left(child1)) shouldBe Left(child1.inject[ExpectedError])
      exec(Left(child2)) shouldBe Left(child2.inject[ExpectedError])
      exec(Left(child3)) shouldBe Left(child3.inject[ExpectedError])
    }
  }

  "errorIs should assert the type of the error (used when IDE cannot infer the type)" in {
    import PureExamples._

    g_E123.errorIs[E1 :+: E2 :+: E3 :+: CNil] shouldBe Right("")
    b_E123_1.errorIs[E1 :+: E2 :+: E3 :+: CNil] shouldBe Left(e1.inject[E123])
    b_E123_2.errorIs[E1 :+: E2 :+: E3 :+: CNil] shouldBe Left(e2.inject[E123])
    b_E123_3.errorIs[E1 :+: E2 :+: E3 :+: CNil] shouldBe Left(e3.inject[E123])
  }

}
