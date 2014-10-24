# AWS SNS Lift Module

Provides a wrapper around the Amazon Web Service Simple Notification Service.

## Using this module

1. Include this dependency:

         // For Lift 2.5.x (Scala 2.9 and 2.10):
         "net.liftmodules" %% "aws-sns_2.5" % "1.0.2"

         // For Lift 2.6.x (Scala 2.9 and 2.10):
         "net.liftmodules" %% "aws-sns_2.6" % "1.0.2"

         // For Lift 3.0 (Scala 2.11):
         "net.liftmodules" %% "aws-sns_3.0" % "1.0.4-SNAPSHOT"

2. Configure your connection:

	You need to supply:
	* AWS access key and secret
	* the topic ARN you have configured in the AWS management console
	* the path in your Lift app you want to register for receiving notifications
	* the public host port and protocol of your Lift app.

	In `Boot.scala`:

        val config = SNSConfig(
         AWSCreds("accessKey", "secret"),
         "arn:aws:sns:us-east-1:something:topic",
         "sns" :: Nil,
          "66.123.45.678",
          9090,
      	  Protocol.HTTP
        )

3.  Register a handler and initialise in `Boot.scala`:

        val sns = SNS(config) {
           case s => println("GOT AN "+s)
        }

        sns.init

  Your handler function will be passed the `Message` field value from the JSON suppled by SNS.


4.	Publish notifications:

	    sns ! Publish("my message")


## Notes

Uses SBT 0.13

### If you need to set the jetty port in SBT:

In your `build.sbt`:

    port in container.Configuration := 9090

Or temporarily from the shell:

    sbt> port in container.Configuration := 9090

### Creating restricted AWS credentials

Yeah, you should definitely do that, rather than use your AWS account login.


