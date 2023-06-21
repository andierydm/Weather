package util

sealed class Either<out R, out L : Throwable> {
    inline fun <Q, reified M : Throwable> wrap(f: (v: R) -> Either<Q, M>): Either<Q, Throwable> {
        return when (this) {
            is Right -> f(value)
            is Left -> Left(error)
        }
    }
}

class Right<R>(val value: R) : Either<R, Nothing>()
class Left<L : Throwable>(val error: L) : Either<Nothing, L>()