package reader.network

import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.Matchers

import reader.EventShouldFireWrapper.convertToEventShouldFireWrapper

class UrlCheckerSpec extends FunSpec with Matchers with BeforeAndAfter {
  var checker: UrlChecker = _

  before {
    checker = new UrlChecker
  }

  describe("The url checker") {
    it("should accept valid urls") {
      checker.urlIsValid shouldFireIn {
        checker.check("http://feeds.rssboard.org/rssboard")
      }
    }

    it("should not accept invalid urls") {
      checker.urlIsInvalid shouldFireIn {
        checker.check("garbage")
      }
    }
  }
}
