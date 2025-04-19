# EzCache

EzCache is a Redis-like in-memory cache server built using Java

### Description
This is a "Redis"-like server, with support for RESP v2 protocol for serialization and deserialization, and a subset of Redis commands: PING, ECHO, GET, SET (with NX, XX, EX and PX options), DEL, EXISTS, INCR, DECR, LPUSH, RPUSH, LRANGE, FLUSHALL and SAVE. It seamlessly works with the Redis CLI, as well as the Jedis (Java) Client for Redis.

### Setup

You need `Java 24`+ because this project uses virtual threads.
Some earlier versions of Java would need `--enable-preview`.

- Install `Java 24`
- Set `JAVA_HOME` and add to `PATH`
- Download `Maven`
- Setup `MAVEN_HOME` and add to `PATH`

### Check out this project from GitHub

Run `git clone https://github.com/rxrav/EzCache.git` in git-scm

### Downloading dependencies

If you're using an IDE like IntelliJ IDEA, you know your way around :) Just open the `pom.xml` and load Maven changes.

However, to download all Maven dependencies, you can `cd` into the project base folder, same place where the `pom.xml` is
and run `mvn dependency:copy-dependencies`. This will kickstart the download.

We use:
- Log4J2 for logging
- Jedis, used as a client to test the EzCache server
- Jackson, used to convert Maps into JSON strings and vice versa
- JUnit5, for unit tests

### Running tests

Open powershell (or terminal), `cd` into the projects base directory.

If you have set up everything correctly, running `mvn test` should run all the tests.

### Package

Open powershell (or terminal), `cd` into the projects base directory, if you are not already there.

Run `mvn clean package -DskipTests` to package everything into an uber jar without running tests again.

Run `mvn clean package`, does as above, but also runs the tests.

*(Uber jar or fat jar, is a Java (jar) executable, which contains the Java code compiled with all external dependencies)*

### Run

At this point you should have the uber jar ready to run in the `target` directory, named `ezcache-1.0-SNAPSHOT.jar`

To run the jar, execute `java -jar .\target\ezcache-1.0-SNAPSHOT.jar` from the projects base directory.

(In IntelliJ IDEA, run the `main` method in `App.java`)

### Playground

Now, you can connect to this server using RedisCLI and execute the supported commands.

### Redis for Windows by Microsoft

Microsoft Archive: https://github.com/microsoftarchive/redis/releases

Download the `msi` for Windows and install, it will create a folder named `Redis` inside `C:\Program Files`
which will contain, `redis-cli.exe` and `redis-benchmark.exe`. It will also contain the `redis-server.exe` :)

### Performance

```bash
redis-benchmark.exe -t SET,GET -q

SET: 30590.39 requests per second
GET: 33123.55 requests per second
```

*This has been tested on my HP Laptop (Intel Core i5 + 8GB, running Windows 10), keeping in mind Java's WORA principle,
I trust this will run the same way on other OS with JRE 21 installed on it.*

### Connect with me on LinkedIn

Link to my [LinkedIn profile](https://www.linkedin.com/in/sauravdey/)