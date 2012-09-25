# AWS SNS Lift Module

Provides a wrapper around the Amazon Web Service Simple Notification Service. 

## Using this module

1. Add the following repository to your SBT project file:

    For SBT 0.11+:

        resolvers += "liftmodules repository" at "http://repository-liftmodules.forge.cloudbees.com/release/"

    For SBT 0.7:

        lazy val liftModulesRelease = "liftmodules repository" at "http://repository-liftmodules.forge.cloudbees.com/release/"

2. Include this dependency:

         "net.liftmodules" %% "aws" % "sns" % (liftVersion+"-1.0.0")

3. Configure your connection in `Boot.scala`:

	You need to supply AWS access key and secret, the topic ARN you have
	configured in the AWS management console, the path in your Lift
	app you want to register for receiving notifications, and 
	the host port and protocol of your Lift app.

       val config = SNSConfig(
         AWSCreds("accessKey", "secret"),
         "arn:aws:sns:us-east-1:something:topic",
         "sns" :: Nil,
          "66.123.45.678",
          9090,
      	  Protocol.HTTP
       )

4.  Register a handler and initialise in `Boot.scala`:

        val sns = SNS(config) {
           case s => println("GOT AN "+s)
        }

        sns.init
               

5.	Publish notifications:

	    sns ! Publish("my message")              


## Notes

Uses SBT 0.11.2

### If you need to set the jetty port in SBT:

In your `build.sbt`:

    port in container.Configuration := 9090

Or temporarily from the shell:

    sbt> port in container.Configuration := 9090

### Creating restricted AWS credentials

[to describe]

