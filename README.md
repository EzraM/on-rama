# On Rama

My first thought after learning about [Rama](https://redplanetlabs.com/docs/~/clj-defining-modules.html) was, "this is aimed at me."
After reading through some of the docs, my thought was "I have got this."
After coding for awhile, it was, "yeah, I've mostly got this."
After coding a bit longer, I thought, "wow, this learning curve is steep."
And, where we are now is, "Rama is a very intriguing system."

I'm a software engineer and I have a young kid, so the time I have for learning new things is often short, almost borrowed. The community that, for many years, has dominated my learning time is Clojure. And, a fascinating project to come out of the Clojure world this year is a system by Nathan Marz called Rama.

Rama aims to play near the bottom of the stack, turning an application backend into a series of stream processing puzzles to be solved. In a Rama system, the database is no longer the production source of data. Production data starts as events sent to Rama.

People are wisely conservative at the database layer of the application. There are a lot of software systems out there, and most have a database at the bottom of the stack. A database does a lot, and is one of the most battle-hardened systems we have. So why be open to considering something else in that slot?

To understand why I think Rama could be worth the time to learn -- and boy does it takes time! -- it helps to know a bit more of my background.

## My background with event sourcing and scaling with relational databases, the good and the bad

I've worked on a large, event sourced application that did not have a relational database at the bottom of the stack. It was built on the ideas of domain driven design. The bread and butter of the system was events being streamed through functions that projected those events into simple data structures.

In this section, we'll look at the strengths and shortcomings of that system. Note, a Rama system would have some different strengths and weaknesses. The larger point of this section is to start off with our heads in an enterprise scale, event-driven world, to understand the high-level opportunities and potential challenges.

There is a function called `reduce` that captures the idea of event-sourcing. Here's how reduce works in Javascript:

```javascript
const events = [{ user: "Jim" }, { user: "Jane" }, { user: "Joanna" }];
// function that takes an event and projects it into a state
const reducer = (state, event) => [...state, event.user];

// reduce begins by calling the reducer function with startingState and the first event
// the result of this is the next state, which is used when we call the function again along with the next event
// reduce continues updating the state based on events until there are no more events left, and then returns a result
const startingState = [];
const state = events.reduce(reducer, startingState);
// state is ['Jim', 'Jane', 'Joanna']
```

You start with events, and create simple data structures by streaming those events through a transform function. This pattern of reducing over events has become widespread in frontend development, where these reducers are used to update the state of ui components.

In the backend system I worked on, events were stored in a database called EventStore. On top of EventStore, the company wrote a system called Foundation. Foundation handled everything to do with processing events, defining and deploying microservices, and storing the sometimes large, json-based projections. Projections were the result of pulling bits from many events and building them into a data structure. Foundation was developed by a team over multiple years.

Like all systems, there were pros and cons. Some cons were:

- Foundation was a lot to develop in-house.
- Eventual consistency was sometimes tricky. When a user tells the system "I've changed this field", when does the system say "yes, we've saved your change"? It was difficult to know when to declare victory or show an error.
- Events, once written, sat unchanged. But, the behavior of the application changed. So, there were multiple versions of events. As an engineer, you'd want to be aware of changes between event versions, and from what point in time to start processing.
- Over time, we had a lot of events, and it's likely that multiple terabytes of those events had little business value.

Some pros were:
Projections were nice. Say you had a page in a user interface which displayed 10 fields. Many times, those fields could be stashed together, ready for page load, behind an 'id' lookup in a big object. Anytime one of the fields was updated, the data structure was updated too. Simple data structures that matched the shape of your application could get you far. And, the data could be kept fresh.

Often, when I feel pressure to add a cache to a program to solve a performance issue, what I really want is a projection. A cache is reactive -- you do an expensive lookup once, and then at the second request, you have the data in hand. A projection can be proactive. You have all the values in the format you need at your fingertips, and can respond to the first request with the same speed as those that come later.

Foundation had a workflow system used to handle things like insurance policy renewals. Workflows were almost magical. Workflows had multiple steps, and at the start and end of each step, Foundation would checkpoint progress. You could imagine a renewal workflow starting at a scheduled time, doing it's thing until it ran into an unexpected problem partway through. Maybe a carrier endpoint timed out. The workflow would enter an error state, and you could see that on a dashboard. The cool part was, you could retry the workflow starting at the step that went sideways, from the last checkpoint. You could do this a day or more after the problem first appeared. The workflow could start from the point where it had gotten stuck and, most times, it would finish successfully.

Scaling was a mixed story. Here's how the system was supposed to scale: imagine a room with long shelves and little piles of events/papers sitting on the shelves. One pile of events might be related to the history of a policy. Another pile could be the events related to someone creating an account. The piles were called "aggregates," the term used in domain driven design. Users of the system were often interested in different piles, so conflicts were rare. You couldn't change events, so there wasn't locking at the event-store layer when it came time to write new events. Lots of people could be looking at different piles of papers in the big room, and everyone would get along, and the system could scale gracefully.

Most times, the architecture worked well. Sometimes, we ran into problems. One issue was the size of projections. We had a projection to hold basic policy information for all policies in the system, and that projection ended up being large, running up against the limits of what Foundation was designed to handle. Rama has a similar ability to make projections, but they can be much larger than memory, distributed, and replicated, but we'll get to that later.

You were limited in how you could query a projection. Say you had a grid widget on screen and you wanted to filter and sort the data somehow. Most apps have these grids. In those cases, we piped the data into Elasticsearch, and used the search index.

The ugly was the time a large carrier ran a test where they rolled out the application to many of their employees over the course of some weeks. The application buckled under the pressure of the sharply increased load. I don't remember the specifics of what didn't scale, but preparing the platform to handle that volume would have been an intensive project. The carrier had no appetite for the risk and was unimpressed. Had the application been able to scale, I imagine the team would have had a shot at a transformative contract.

In my memory, the event-sourced system was one of the most interesting that I've been a part of.

Nowadays, I'm at a different company, working on a very different project, which has, at the bottom of the stack, a relational database. Many of our bottlenecks are database-related. Queries are slow because of big tables and lots of joins. There are tables that have become too-big-to-join. Queries lock each other. The system as a whole is up or down, fast or slow, based on the database. The path toward more performance is not straightforward, and there's pressure to pull data out from the database and into caches, or search indexes, or third party services, or other databases.

Each time we pull something out, the system as a whole could become more performant. But also, the system becomes less cohesive, harder to run as a whole, harder to test, and harder reason about. Is the data wrong because the cache is out of date? How long should it take for data to make it into the search index?

The work we're doing to fix the system often doesn't feel like making the whole more elegant.

And so it's this familiarity with event sourcing, it's promises and pitfalls, and also some familiarity of places where relational databases fall short, that brings us back to Rama.

The value prop of Rama is to buy a scalable, feature-filled, event-sourcing foundation. Had Rama been available years ago, the company I was a part of could have shaved off a lot of developer time, and had a stronger base on which to develop their business features. Yeah, Rama does not come with a workflow engine or workflow UI out of the box, that would still have to built. But a lot of the rest is in there.

## Pedigree of Rama

Rama is a project by Nathan Marz. Marz has written multiple big data systems in Clojure. These include Cascalog, which was an ambitious Datalog-on-Hadoop project that had niche adoption. Marz also wrote Storm, for fault-tolerant stream processing. Storm was in some ways less ambitious than Cascalog. Storm also had much wider adoption because Storm had a good story for being used from idiomatic Java code. Storm was the reason the company Marz worked for was acquired by Twitter.

At Twitter, Marz saw up-close the company hyperscaling. This was the fail-whale era, when the service was frequently down. Marz thought deeply about the challenges they faced. Rama comes from someone with a hacker's heart, who also understands the value of clear thinking and clear writing. Marz says his thinking was sharpened by writing the book "Big Data" in 2015. (I haven't read the book.)

Rama was released in 2023, and was the result of 10 years of intensive development. The seed round to help launch the company was lead by Garry Tan, now president of Y Combinator. Rama was launched along with a demo system which implemented the features of the original customer-facing Twitter. Using generated Tweets, they showed the system running at 3 times the scale of current-Twitter/X with linear use of resources.

Rama-Twitter had fewer lines of code than an open-source project called Mastodon, which has a similar feature set. The Mastodon code was built using Rails on top of a relational database. The Mastodon-Rails code would have no ability to scale to anywhere near where the Rama version could.

The Rama carrot is to write a small project and, if needed, scale it up with predictable performance at each step.

## Begin the climb

So if you are here, then you're close to edge of a great, steep cliff. You're looking up, strapped into your harness, and are thinking about first steps. There might be something up there. You can see it glinting, but it's unclear what it might be.

Most Rama documentation uses the Java API. I'm here for the Clojure. That does mean translating Java-Rama to Clojure-Rama sometimes.

### From scratch

My go-to text editor is VSCode. For Clojure, I use the Calva extension.

You can start from scratch in an empty directory with these files:

```
deps.edn
src/events.clj
```

Clj-kondo is supported by following these steps:
https://github.com/redplanetlabs/rama-clj-kondo/blob/master/README.md
Copy com.rpl from a clone of the GitHub repo into the local .clj-kondo directory

deps.edn

```
{:paths ["src"]
 :deps {com.rpl/rama {:mvn/version "0.11.0"}
        com.rpl/rama-helpers {:mvn/version "0.9.0"}}
 :mvn/repos {"nexus-releases" {:url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}}}
```

src/on_rama/events.clj

```clojure
(ns on-rama.events
  (:require [com.rpl.rama :as rama]
            [com.rpl.rama.test :as rtest]))
```

Start the repl using Calva jack-in and select deps.edn.

### Write an event

| Code: `write_event.clj`

Data enters Rama as events. In the Clojure examples from Red Planet Labs, the shape of an event is often defined this way:

```clojure
(defrecord AddUser [user-id])
```

Events are consumed by depots. Depots live in modules.

A module is what gets deployed to a rama cluster. Modules have some ownership of the resources that are attached to them. Thinking "microservice" is close. When testing locally, as we are, modules will run in the small, in memory, with simulated threads and workers. When deployed in the large, modules can be run on many different worker machines. And, they can run on multiple threads of those machines.

We will define a module with one depot.

```clojure
(rama/defmodule WriteEventModule [setup topologies]
  (rama/declare-depot setup *users (rama/hash-by :user-id)))
```

Notice that our definition of a module and depot is pretty short. I haven't run into a microservice that can be defined in similarly few lines of code.

Within the module, the name of the depot starts with a `*` character. This is important, as it tells Rama that this is a variable whose bindings should be managed in Dataflow-land.

To write an event to a depot, Rama wants to know two things about the event we'll pass in:

- how do I turn this event into bits?
- in which partition do I put these bits?

Take a moment and realize this is pretty different from the relational database world. In a typical database scenario, you describe a high-level architecture about your data. You write create-table statements, and column definitions, with types for the columns and maybe a foreign key constraint for good measure. The database is there to manage your data, and it is very interested in the format that data will take. It will soon be collecting column-level statistics and doing all sorts of interesting bookkeeping as the proud new owner/manager/operator of your data.

As for partitioning a relational database... at scale, you might configure partitioning. This would be after you won your third game of rock-paper-scissors versus the database administrator. Partitioning would be done when people are about out of other ideas.

In this Rama world, we've flipped that all on its head. We are thinking about our partitioning _first_, and all the rest of the stuff about the format of the data is left as an exercise to the user. It's bits to Rama. Rama cares so little about the content of events that the way events are serialized and deserialized is pluggable.

But that partition... that partition is important! That partition could, in the future, be glorious. When learning, the partition will cause pain.

Before, we talked about our events as living in a big room, with long shelves, and each event being a piece of paper in a little pile along the long shelves. Part of the way an event sourced system can scale well is by taking advantage of the fact that people are likely interested in different little piles of paper. And, the more often someone goes to a stack of paper and finds everything, or most everything, they need, the faster the system will run. And, the more often someone goes to a stack of paper and realizes they need something from somewhere else... way across the room... the more that happens, it will tax the system.

So Rama draws our attention to the idea of partitioning on line 2 of our first program. Part of the design of a Rama program is to decide how we'll sort our paper piles, early on. When we feed events to this example user depot, the events will be partitioned by user-id.

So now, with a little fanfare, we are ready to run the module and write the event to the depot.

```clojure
;; Run a module, write the event
(with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc WriteEventModule {:tasks 4 :threads 2})
    (let [module-name (rama/get-module-name WriteEventModule)
          log (rama/foreign-depot ipc module-name "*users")]
      (rama/foreign-append! log (->AddUser 1)))
  )
```

When you append a Clojure record to a depot, Rama will use nippy to serialize the content. Depot events can be created, but they cannot be destroyed nor changed. The cost of storing events will grow over time.

To get a feel for events, lets add another event, now with a different field:

```clojure
(rama/foreign-append! log {:user-id 2 :status :active})
```

That event also goes into the depot.

## Read an event

| Code: `read_event.clj`

We've covered writing an event, but how do you read it?

To read events requires setting up a topology within the module. A topology is a like a highway map that covers the flow of data from a depot to all of the places it is used downstream.

```clojure
(let [s (rama/stream-topology topologies "users")])
```

When we have our stream topology, we can call the `<<sources` macro to begin writing dataflow code.

Rama Dataflow is a language built on top of Clojure with its own variable binding, if statements, and conditions. Almost all the functions I've used inside of Dataflow come from the Rama namespace, except println. Other Clojure functions can be used, and we'll cover that in a bit.

But first, to read from a depot, use the `source>` function.

```clojure
(rama/defmodule ReadEventModule [setup topologies]
  (rama/declare-depot setup *users (rama/hash-by :user-id))

  ;; topology is the map of how data will flow
  (let [s (rama/stream-topology topologies "names")]
    ;; we define our ETL using Dataflow language, and the <<sources macro
    ;; drops us into Dataflow-land
    (rama/<<sources s
                    ;; we subscribe to the *users depot, emitting each time
                    ;; an event is sent there.
                    ;; we destructure the event, assigning a value from event
                    ;; to a Dataflow-land variable
                    (rama/source> *users :> {:keys [*user-id]})
                    ;; we print the Dataflow-land variable using a normal Clojure function
                    (println (format "User id %s" *user-id)))))
```

The first argument to source is the name of the depot we'll be reading events from. Think of the `:>` token as "on emit". Each time the source depot \*users emits, feed the latest event to the function that appears next. Here, we destructure the event, pulling out the user-id field.

Note, when destructuring, the name of the field we assign to has a `*` at the front of it, because the variable belongs to Dataflow-land.

Run the module to see the logs.

```clojure
(with-open [ipc (rtest/create-ipc)]
  (rtest/launch-module! ipc ReadEventModule {:tasks 4 :threads 2})
  (let [module-name (rama/get-module-name ReadEventModule)
        log (rama/foreign-depot ipc module-name "*users")]
    (rama/foreign-append! log (->AddUser 1))
    (rama/foreign-append! log (->AddUser 2))))
```

## Clojure functions and Dataflow

| Code: `dataflow_language.clj`

Clojure functions work in Dataflow-land but they can look a little different.

A quick way to enter Dataflow-land is through the Rama function `?<-`.

A quick check that the regular Clojure function `println` works as you'd expect is to run:

```clojure
(?<- (println "Hello!"))
```

The `println` function is used for side effects. It takes input, in this case a string, prints to the console (the effect), and we do nothing with the output. Functions run for side-effects from Dataflow code look the same as they do in vanilla Clojure.

When we have a function and we want to do something with the output, we can use the emit token, `:>`:

```clojure
(?<-
  (inc 5 :> *six)
  (println *six))
```

In Dataflow-land, a Clojure function is treated as a data source that, when called, emits once.

A common pattern in Rama is to get a stream of events from a depot, and do something with each of those events. To make that pattern without the overhead of a module, we can use `ops/explode` on a vector of values.

Ops are in this namespace:

```clojure
[com.rpl.rama.ops :as ops]
```

```clojure
(?<-
 ;; emit each user record one at a time, destructuring the user-id
 (ops/explode [{:user-id 1} {:user-id 2} {:user-id 3}] :> {:keys [*user-id]})
 (println *user-id))
```

In this contrived example, we run a Clojure function for each of our events, then capture the output emitted by that Clojure function into a new Dataflow variable:

```clojure
(?<-
 (ops/explode [{:user-id 1} {:user-id 2} {:user-id 3}] :> {:keys [*user-id]})
 (inc *user-id :> *inc-user-id)
 (println *inc-user-id))
```

This Dataflow prints once for each of the events, 2, 3, 4.

Dataflow language is a rabbit hole that goes deep. There's variable unification and a batch sub-system that supports Datalog-like joins. But for now, we want to take a step back again, and consider how to query for basic information about the events that have entered our application.

## Transforming an event into a pstate

| Code: `simple_pstate.clj`

In SQL, to add data, we INSERT a record to a table. In Rama, to add data, we append an event to a depot.

In SQL, to query data, we SELECT from a table. In Rama, we do not SELECT from a depot to query. We, instead, create an index-like data structure, called a pstate, and then query that.

We use Dataflow language to read the events we want, and transform them into the pstate. The word pstate is short for `partitioned state`. It is a reminder that the data structure we are making, in a production environment, is going to be saved to disk, replicated, and partitioned.

The shape of the pstate structure is largely up to us. The first one we'll make is map of user id's to usernames, declared as a map of a Long to a String:

```
{ Long ; user-id
  String ; username }
```

This is a lookup where, if we know someone's user-id, we can get their username. We declare a pstate inside of a module and topology.

```clojure
(let [s (rama/stream-topology topologies "users")]
  (declare-pstate s $$usernames {Long ; user-id
    String ; username
    }))
```

Then we can write to the pstate using Dataflow code. First, we call `(<<sources s)` to tell Rama that we're writing Dataflow. Then, we `source>` from a depot, emitting each event into a new variable. This looks like so:

```clojure
(<<sources s
  (source> *users :> *user))
```

Now that we have a single \*user, we can save the user into the pstate structure. In Rama, there is a subsystem called `Paths` or `Specter` that is used to write and to read from data structures like pstates.

### A simple path for reading

How does Specter work? Sometimes, when driving, you have directions that break down a route into a set of small steps. These small navigations read like:

- turn left onto I-80 at exit 6
- stay on I-80 for 100 miles

To complete the metaphor, in Specter, a `path` is all the steps used to complete a route. Each step along the way is called a `navigator`.

Here are what a couple paths look like:

```
[] ; a path with no navigators
[(keypath 2)] ; a path which navigates by id to the key 2
[(keypath 2) (nil->val false)] ; a path which navigates to key 2, and if there is nothing there, returns false, and if there's something there, selects the value at key 2
```

CSS and jQuery, if you squint, use similar schemes for navigating.

### A simple path for writing

| Code: `simple_pstate.clj`

A path that writes into a data structure navigates to a spot and then makes a change. A simple change is to set a value. To do that, we use a function called `termval`.

```clojure
[(keypath 2) (termval "alexander-the-great")]
```

In our case, we want to write to the user-id key with the value username. The Dataflow snippet to read events from a depot and then write to a pstate looks like this:

```clojure
(<<sources s
          (source> *users :> {:keys [*user-id *username]})
          (local-transform> [(path/keypath *user-id) (path/termval *username)] $$usernames))
```

Once a value is in a pstate, we can get it from outside of Rama and Dataflow-land using functions like foreign-select-one. Here, usernames is a reference to the pstate from an instance of the running module.

```clojure
(rama/foreign-select-one (keypath 1) usernames)
```

## Working with sets

| Code: `sets.clj`

I like using sets, and also have found using sets with pstates to have some gotchas. The code for this bit is in the file `sets.clj`.

We'll make a pstate to hold a set inside of a map: `{Long (set-schema Long {:subindex? true})}`.

We use the `set-schema` function to say "this is a set of Long's", and then use the subindex option. Subindexing means that the amount of data inside of the set can become very large, larger than memory, with each value stored in order. Rama does not support top-level sets, so our sets are nested in a top-level map. When navigating the topmost map, remember to write to and read from the same partition!

To add an element to a set, there are a few navigators used.

```clojure
(rama/local-transform> [(path/keypath *swing) path/NIL->SET path/NONE-ELEM (path/termval *kid)] $$swing-sessions)
```

This is what the navigators do:

- (keypath \*swing) is the first hop. Sets can't be top-level, so we find our set inside the map.
- NIL->SET is a token that says, if we've landed at a place with no value, initialize an empty set
- NONE-ELEM says, select an empty bubble inside the current selected set. We could think of this as a fuzzy bubble marking a spot where a new value could be placed.
- (termval *kid) says, write the value of the *kid variable into the current selection, which happens to be the fuzzy bubble inside of our set.

So now we have a set that should have a value in it. The next question becomes, how do we check that the value is actually there? How do we check if a value is contained in a set?

We can use a query that looks like this:

```clojure
(<<query-topology topologies "kid-used-swing?"
                  ;; queries take inputs and emit results.
                  [*kid *swing :> *used-swing?]
                  ;; this data is partitioned by the swing, so we go to the partition
                  ;; of the swing we are interested in.
                  (|hash *swing)
                  ;; this is an anonymous function that will be called as part of the path.
                  ;; the argument to the function is the set of kids who used the swing, pulled from the pstate.
                  (<<ramafn %had-kid [*kids]
                            ;; we use the baked-in contains? function from Clojure to check
                            ;; if the kid we're interested in is in this set, and emit the answer.
                            (:> (contains? *kids *kid)))
                  ;; this path resolves with a view that a emits of a boolean of whether or not a kid used the swing.
                  (local-select> [(path/keypath *swing) (path/view %had-kid)] $$swing-sessions :> *used-swing?)
                  ;; all queries must, as their last operation, relocate to the partition where the query started.
                  (|origin))
```

The `view` function is interesting. It pulls values from a pstate based on the current path selection, and passes those values as an input to a `ramafn`. I think of `ramafn` as a portal out of Dataflow-land into normal Clojure. Here, we have navigated our selection to a set. Our ramafn has that set as an input. And, we can call the built-in Clojure function `contains?` on the set as a check.

It took me awhile to get simple operations on sets working in Rama. I was expecting reading from a set and writing to set to be one-liners, or maybe nearly so. Instead, there's a lot more going on, but once you see the pattern, the different parts do fit together.

## One path to buy-in on an existing application

So we started high level, and have also gone a little into the weeds with Rama. Knowing I haven't rolled Rama into production myself, this next bit is a thought experiment on how one could go about getting buy-in to pull Rama into an existing project.

Many application teams are pretty attached to their databases. Many application teams are also much-less attached attached to their caches. If you look at an architecture diagram, you'll see the cache is in a very privileged position, though! It's the user's first stop for production data. That's interesting... that's where we might want Rama to be.

So we start and tell people, we want to try a more programmable cache. You suspect this cache could help make the application more performant in the future. To start, though, all we want to do is replace Redis in a few places with this Rama thing.

You start using Rama by doing what the cache was doing. This gets you and everyone familiar with Rama. This verifies the performance. This gets folks used to paritioning and the rest.

Then a little time passes, and we find a place where the caching isn't working great. Maybe we're getting hammered on cache misses, but we know what the possible values are, and we could make things faster using a pstate that isn't even that big. So, we start proactively shuttling some data to Rama, to be used in this one place for a simple pstate look up.

Do that a few times, and if it works well, there's a nice path for Rama to grow.

## Conclusion

Rama is a very intriguing new system.

If you've worked with event sourcing and liked it, Rama is likely to make sense to you. You'll see the places where Rama reduces boilerplate and makes available foundational features that could also be cost-prohibitive to build on your own.

If you're interested in learning event sourcing, Rama is a powerful perch to start from. And, if you use Clojure to do it, the code is nice to look at too.

The pedigree of the people behind Rama is good, and the principles feel solid as well. Rama draws our attention to where data lives, where it's partitioned, and takes less interest in how individual records are structured.

The Rama promise is to write small systems that are able to work at large scale. In some ways, the Rama promise is similar to the promise lisp has always had, that sharp tools can give meaningful leverage. It makes sense that Rama is written in Clojure, the modern lisp.

There's a lot we don't know about Rama. One of the weaknesses I've seen in existing event-sourced systems is that projected data structures do not easily support ad-hoc queries -- filter by first name, order by last name, wait scratch that!, order by id, and sort by last modified. Relational databases solve that problem well.

If your application has well-understood workflows, and you're running those flows a lot of times, Rama looks promising.

If you wanted to try Rama in an existing application, you'd likely start small, maybe with a subsystem like a cache.

If Rama turned out to be a good fit for your domain, you might find yourself writing one of your most elegant systems yet.

## References

rama-demo-gallery, Clojure code examples
https://github.com/redplanetlabs/rama-demo-gallery/blob/master/src/main/clj/rama/gallery/profile_module.clj

Clojure docs
https://redplanetlabs.com/clojuredoc/index.html

Introduction to Specter (paths)
https://www.youtube.com/watch?v=VTCy_DkAJGk&t=1295s

Rama / Electric app
https://github.com/jeans11/demo-rama-electric
