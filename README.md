# lein-docker

[![Clojars Project](http://clojars.org/arohner/lein-docker/latest-version.svg)](http://clojars.org/arohner/lein-docker)

A Leiningen plugin to perform simple docker tasks. Mainly intended for use in continuous-deployment pipelines.

## Usage

In your project.clj, add a key:

```clojure
  :docker {:repo "foo/bar"}
```

`lein docker` supports two commands right now: build and push.


### build

    $ lein docker build

Builds a docker image, using the dockerfile at the root of the project
dir. Uses the repo name from the project.clj, and tags the image with
the project.clj version. If the version contains SNAPSHOT, it will be
replaced with the current datetime.

### push

    $ lein docker push [<version>]

Performs a simple docker push. Can optionally pass an explicit
version, or `:latest`, which will push the most recent tag from this
repo.


### lein

    $ lein docker lein <imgId or tag> <lein args>

Performs a `docker run` on image, mounting this project's directory inside the container, then running leiningen inside the container with the supplied args. lein must already be installed on the container.

Optional Project config: In your project.clj or ~/.lein/profiles.clj, add the following to your :docker map
- :sudo true if docker requires sudo to `docker run`
- :ports {} a map, passed to -p for port mapping
- :m2-dest \"/home/username/.m2/\", will -v mount ~/.m2/ to :m2-dest, dramatically speeds up lein deps
  
## License

Copyright © 2015 Allen Rohner

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
