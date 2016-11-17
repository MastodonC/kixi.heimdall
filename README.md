# kixi.heimdall

![Heimdall](https://raw.githubusercontent.com/MastodonC/kixi.heimdall/master/docs/Gosforth_Cross_monsters.jpg)
(image from <https://en.wikipedia.org/wiki/Heimdallr#/media/File:Gosforth_Cross_monsters.jpg>)

This app has been largely inspired by the blog posts [Securing Clojure Microservices using buddy](http://rundis.github.io/blog/2015/buddy_auth_part1.html).

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

The apps requires a private key and public key (RSA), to be generated with

```
openssl genrsa -aes128 -out auth_privkey.pem 2048
openssl rsa -pubout -in auth_privkey.pem -out auth_pubkey.pem
```

This should be moved to the resources/ folder.

There should be a configuration file in the home directory called *.heimdall.auth-conf.edn* , with following structure:

```
{
  :privkey "key_file_name.pem"
  :passphrase "secret-key"
}
```

The app has one significant URL: '/create-auth-token' which takes a username and password parameter in json format.

## Development

You can add seed data for development using the following command:

```
lein seed development
```

## Deployment on mesos

Assuming that mesos is running on AWS architecture.
docker build an image with an extra argument to specify the S3 bucket the secrets are stored in.

```
docker build --build-arg SECRETS_BUCKET=<bucket> -t mastodonc/kixi.heimdall .
```

The deployment file to post to marathon can be built using the deploy.sh script


```
./scripts/deploy.sh <mesos-admin-lb> staging mastodonc/kixi.heimdall
```
(substituting mastodonc/kixi.heimdall in the snippets above by the desired docker image name)

## Running

Heimdall uses docker and docker-compose to manage external dependencies in development:

```
docker-compose up
```
(Wait until the system has settled before proceeding)

To start the application, run:

```
lein run -m kixi.heimdall.bootstrap -p <development/production>
```

## License

Copyright © 2016 Mastodon C
