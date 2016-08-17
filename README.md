# kixi.heimdall

![Heimdall](https://raw.githubusercontent.com/MastodonC/kixi.heimdall/master/docs/Gosforth_Cross_monsters.jpg)
(image from <https://en.wikipedia.org/wiki/Heimdallr#/media/File:Gosforth_Cross_monsters.jpg>)

This app has been largely inspired by the blog posts [Securing Clojure Microservices using buddy](http://rundis.github.io/blog/2015/buddy_auth_part1.html).

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

The apps requires a private key (RSA), to be generated with

```
openssl genrsa -aes128 -out auth_privkey.pem 2048
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


## Running

To start a web server for the application, run:

    lein ring server

## License

Copyright © 2016 Mastodon C
