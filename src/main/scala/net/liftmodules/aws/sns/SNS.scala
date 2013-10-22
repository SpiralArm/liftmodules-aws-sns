/*
 * Copyright 2012-2013 Spiral Arm Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.liftmodules.aws.sns

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.ConfirmSubscriptionRequest
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.model.SubscribeRequest
import com.amazonaws.services.sns.model.UnsubscribeRequest

import net.liftweb.actor.LiftActor
import net.liftweb.common.Loggable
import net.liftweb.http.LiftRules
import net.liftweb.http.OkResponse
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JString
import net.liftweb.json.parse
import net.liftweb.util.Schedule

/**
 * Provides functionality for registering with Amazon SNS, publishing messages to SNS, and receiving
 * messages from SNS.
 *
 * Register the function you want to use for messages received from SNS, for a given
 * SNS configuration. For example:

  // Boot.scala
  import net.liftmodules.aws.sns._

  val config = SNSConfig(
      AWSCreds("accessKey", "secret"),
      "arn",
      "rest" :: "path" :: Nil,
      "127.0.0.1",
      8080,
      Protocol.HTTP)

  val sns = SNS(config) {
    case s => println("I received message: "+s)
  }

  sns.init

  // SomeSnippet.scala
  sns ! Publish("Hello world")

 *
 */
case class SNS(config: SNSConfig)(handler: SNS.HandlerFunction) extends RestHelper with LiftActor with Loggable {

  import config._

  lazy val client = new AmazonSNSClient(new BasicAWSCredentials(creds.access,creds.secret))

  // A successful subscription to SNS returns us a subscription ID, which we use to unsubscribe on shutdown.
  private[this] var subscriptionId: Option[String] = None

  def init:Unit = {
      LiftRules.statelessDispatch.append(this)
      LiftRules.unloadHooks.append(() ⇒ unsubscribe)
      this ! Subscribe()
  }

  serve {
    // Annoyingly, SNS JSON POSTs have the mime-type of text/plain
    // http://docs.amazonwebservices.com/sns/latest/gsg/json-formats.html
    case config.path Post post ⇒
      post.body.foreach(dispatchJson)
      OkResponse()

  }

  private def dispatchJson(bytes: Array[Byte]): Unit = {
    val s = new String(bytes, "UTF-8") // TODO: is UTF-8 guaranteed by SNS?
    logger.trace("Msg "+s)
    val json = parse(s) // TODO: is a stack trace on parse failure acceptable here?

    (json \ "Type").extractOpt[String] match {

      case Some("SubscriptionConfirmation") ⇒ for {
          JString(token) ← json \ "Token"
          JString(arn)   ← json \ "TopicArn"
        } confirmation(token, arn)

      case Some("Notification") ⇒ for( JString(msg) ← (json \ "Message") ) handler(msg)

      case otherwise ⇒ logger.error("SNS Unknown message %s raw body %s".format(otherwise, s))
    }
  }

  def messageHandler = {
    case Subscribe() if LiftRules.doneBoot ⇒ subscribe
    case Subscribe() ⇒
      logger.trace("SNS Waiting until we have finished booting.")
      Schedule.perform(this, Subscribe(), 5000L)//have a nap and try again.
    case Publish(msg) ⇒ client.publish(new PublishRequest().withTopicArn(config.arn).withMessage(msg))
    case otherwise ⇒ logger.warn("Unexpected msg: "+otherwise)
  }

  private[this] def subscribe = {
      logger.info("SNS Subscribing to endpoint %s".format(ep))
      client.subscribe(new SubscribeRequest().withTopicArn(config.arn).withProtocol(config.protocol.toString).withEndpoint(ep))
  }

  private[this] def confirmation(token: String, arn: String) = {
    subscriptionId = Option(client.confirmSubscription(new ConfirmSubscriptionRequest().withTopicArn(arn).withToken(token)).getSubscriptionArn)
    logger.trace("SNS Confirmation %s".format(subscriptionId))
  }

  private[this] def unsubscribe = {
      logger.info("SNS Unsubscribing from %s uarn %s".format(ep, subscriptionId))
      subscriptionId.foreach { u ⇒ client.unsubscribe(new UnsubscribeRequest().withSubscriptionArn(u)) }
      subscriptionId = None
  }


  private[this] lazy val ep:String =  "%s://%s:%s/%s".format(protocol,address,port,path.mkString("/"))

}



sealed trait SNSMsg
case class Subscribe() extends SNSMsg
case class Publish(msg:String) extends SNSMsg

object Protocol extends Enumeration {
    type Protocol = Value
    val HTTP = Value(0, "http")
    val HTTPS = Value(1, "https")
}

object SNS {
  type Payload = String
  type HandlerFunction = PartialFunction[Payload,Unit]
}

case class AWSCreds(access:String,secret:String)

case class SNSConfig(creds: AWSCreds,
    arn: String,
    path: List[String],
    address: String,
    port: Int,
    protocol: Protocol.Value)
