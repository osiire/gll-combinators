package edu.uwm.cs.gll

sealed trait Parser[+R] extends (Stream[Char]=>List[Result[R]]) {
  val terminal: Boolean
  
  lazy val first = computeFirst(Set()) getOrElse Set()
  
  /**
   * @return The FIRST set for this parser, or the empty set
   *         if the production goes to \epsilon.
   */
  def computeFirst(seen: Set[Parser[Any]]): Option[Set[Char]]
  
  // TODO handle failure somehow
  def queue(t: Trampoline, in: Stream[Char])(f: Result[R]=>Unit)
  
  // syntax
  
  def map[R2](f: R=>R2): Parser[R2]
  
  // operators
  
  def ~[R2](that: Parser[R2]): Parser[~[R, R2]] = new SequentialParser(this, that)
  
  def <~[R2](that: Parser[R2]) = this ~ that map { case a ~ _ => a }
  
  def ~>[R2](that: Parser[R2]) = this ~ that map { case _ ~ b => b }
  
  def ^^^[R2](v: =>R2) = map { _ => v }
}

trait TerminalParser[+R] extends Parser[R] { self =>
  final val terminal = true
  
  /**
   * For terminal parsing, this just delegates back to apply()
   */
  def queue(t: Trampoline, in: Stream[Char])(f: Result[R]=>Unit) {
    apply(in) foreach { f(_) }
  }
  
  override def ~[R2](other: Parser[R2]) = if (other.terminal) {
    new TerminalParser[~[R, R2]] {
      def computeFirst(s: Set[Parser[Any]]) = {
        val sub = self.computeFirst(s)
        sub flatMap { set =>
          if (set.size == 0)
            other.computeFirst(s)
          else
            sub
        }
      }
      
      def apply(in: Stream[Char]) = self(in) match {
        case Success(res1, tail) :: Nil => other(tail) match {
          case Success(res2, tail) :: Nil => Success(new ~(res1, res2), tail) :: Nil
          case (f @ Failure(_, _)) :: Nil => f :: Nil
          case _ => throw new AssertionError    // basically, this should never happen
        }
        
        case (f @ Failure(_, _)) :: Nil => f :: Nil
        case _ => throw new AssertionError    // basically, this should never happen
      }
    }
  } else super.~(other)
  
  def map[R2](f: R=>R2): Parser[R2] = new MappedParser[R, R2](self, f) with TerminalParser[R2] {
    def apply(in: Stream[Char]) = self(in) map {
      case Success(res, tail) => Success(f(res), tail)
      case x: Failure => x
    }
  }
}

trait NonTerminalParser[+R] extends Parser[R] { self =>
  final val terminal = false
  
  /**
   * This method takes care of kicking off a <i>new</i>
   * parse process.  We will never call this method to
   * handle a sub-parse.  In such situations, we will use
   * the trampoline to queue results.
   * 
   * Note: to ensure greedy matching (for PEG compatibility)
   * we define any Success with a non-empty tail to be a
   * Failure
   */
  def apply(in: Stream[Char]) = {
    val t = new Trampoline
    
    var successes = Set[Success[R]]()
    var failures = Set[Failure]()
    
    queue(t, in) {
      case s @ Success(_, Stream()) => successes += s
      case Success(_, tail) => failures += Failure("Unexpected trailing characters: '%s'".format(tail.mkString), tail)
      case f: Failure => failures += f
    }
    t.run()
    
    if (successes.isEmpty)
      failures.toList
    else
      successes.toList
  }
  
  def map[R2](f1: R=>R2): Parser[R2] = new MappedParser[R, R2](self, f1) with NonTerminalParser[R2] {
    def queue(t: Trampoline, in: Stream[Char])(f2: Result[R2]=>Unit) {
      self.queue(t, in) { 
        case Success(res, tail) => f2(Success(f1(res), tail))
        case f: Failure => f2(f)
      }
    }
  }
}

abstract class MappedParser[A, +B](private val p: Parser[A], private val f1: A=>B) extends Parser[B] {
  def computeFirst(s: Set[Parser[Any]]) = p.computeFirst(s + this)

  override def toString = p.toString

  override def equals(other: Any) = other match {
    case that: MappedParser[A, B] => {
      this.p == that.p && this.f1.getClass == that.f1.getClass
    }

    case _ => false
  }

  override def hashCode = p.hashCode + f1.getClass.hashCode
}

/**
 * Used for setting up a trampoline wrapper for disjunction
 * alternatives (for the sake of left-recursion).
 */
private[gll] abstract class ThunkParser[+A](private val self: Parser[A]) extends NonTerminalParser[A] {
  def computeFirst(s: Set[Parser[Any]]) = self.computeFirst(s)

  override def toString = self.toString

  override def equals(other: Any) = other match {
    case that: ThunkParser[A] => this.self == that.self
    case _ => false
  }

  override def hashCode = self.hashCode
}

