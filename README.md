# kixi.heimdall

![Heimdall](https://raw.githubusercontent.com/MastodonC/kixi.heimdall/master/docs/Gosforth_Cross_monsters.jpg)
(image from <https://en.wikipedia.org/wiki/Heimdallr#/media/File:Gosforth_Cross_monsters.jpg>)

This app has been largely inspired by the blog posts [Securing Clojure Microservices using buddy](http://rundis.github.io/blog/2015/buddy_auth_part1.html).

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

The app requires a passphrase-protected private key and public key (RSA), to be generated with

```
openssl genrsa -aes128 -out auth_privkey.pem 2048
openssl rsa -pubout -in auth_privkey.pem -out auth_pubkey.pem
```

These should be moved to the resources/ folder. Ensure the that the key names match those in `resources/conf.edn` under `:auth-conf`.

There should also be a configuration file in the home directory called `.secrets.edn` , with following structure:

```
{
  :dev-passphrase "secret-key-you-used-to-create-pems"
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
Wait until the system has settled before proceeding.

To start the application, run:

```
lein run -m kixi.heimdall.bootstrap -p <development/production>
```

## Development docker image

There's a docker-compose to start all the dependencies
```
docker-compose up
```
in the root directory.

To build a development docker image to use in dev setups, which expects all the dependencies to run on localhost:

```
docker build -t mastodonc/kixi.heimdall-dev -f Dockerfile-dev .
docker run --net=host -p 3002:3002 -p 5001:5001 mastodonc/kixi.heimdall-dev
```

The public key to use in combination with this development setup is the test_pubkey.pem which is in the resources folder.

## The repl to do user and group administration


Beforehand:

```
(go)
(require '[kixi.heimdall.service :as s])
```

To create a user (including sending an event):
```
(s/new-user (:cassandra-session @kixi.heimdall.application/system)
            (:communications @kixi.heimdall.application/system)
            {:username "moo@bar.com" :password "Local123" :name "john doe"})
```

To create a group:
```
(s/create-group-event (:cassandra-session @kixi.heimdall.application/system)
                      (:communications @kixi.heimdall.application/system)
                      {:group {:group-name "the rebellion"} :user {:id "group-id"}})
```
The output in the repl shows the resulting ids, to be used for adding and removing members.

Adding members and removing them
```
(s/add-member-event (:cassandra-session @kixi.heimdall.application/system)
                    (:communications @kixi.heimdall.application/system)
                    "user-id" "group-id")

(s/remove-member-event (:cassandra-session @kixi.heimdall.application/system)
                       (:communications @kixi.heimdall.application/system)
                       "user-id" "group-id")
```

Updating the group (name)/
```
(s/update-group-event (:cassandra-session @kixi.heimdall.application/system)
                      (:communications @kixi.heimdall.application/system)
                      "group-id" "new-name")
```
*Note*: it's important to use the given functions so that an event gets fired off, especially when used in production.

## License

Copyright © 2016 Mastodon C
