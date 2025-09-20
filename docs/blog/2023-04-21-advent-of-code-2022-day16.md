---
title:      "Advent Of Code 2022 - Day 16"
date:       2023-04-21 16:48:57 +1000
categories: blog
authors: [james]
---

# Day 16: Proboscidea Volcanium

See [Day 16](https://adventofcode.com/2022/day/16) for a detailed description of the problem.

<!--truncate-->

> Continuing to solve the Advent of Code 2022 problems
> (see [Advent of Code - Day 1](2023-04-06-advent-of-code-2022-day1.md)).
>
> Links:
> * [Jactl Programming Language](https://jactl.io)
> * [Jactl on Github](https://github.com/jaccomoc/jactl)
>
> To run the example code in this post save the code into file such as `advent.jactl` and take your input from the
> Advent of Code site (e.g. `advent.txt`) and run it like this:
> ```shell
> $ cat advent.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent.jactl 
> ```

## Part 1

With today's problem you are given a set of rooms each with a valve and a set of tunnels leading to other rooms.
For example:
```
Valve AA has flow rate=0; tunnels lead to valves DD, II, BB
Valve BB has flow rate=13; tunnels lead to valves CC, AA
Valve CC has flow rate=2; tunnels lead to valves DD, BB
Valve DD has flow rate=20; tunnels lead to valves CC, AA, EE
...
```

The idea is to try to open as many valves as possible in the given time (30 minutes) to get the maximum flow rate over
the 30 minutes.
Each valve takes one minute to open, and moving from one room to another connected room also takes a minute.
The idea is to find the optimum order to open the valves (there may not be time to open all of them).
Some valves may have a flow rate of 0 and are therefore not worth opening.
The starting point is always with valve `AA`.

Since we have a fully connect set of rooms I decided to calculate the distances between every pair of valves (ones
that had a non-zero rate, at least). I used Dijkstra's Algorithm that I used for the
[Day 12 - Hill Climbing](https://jactl.io/blog/2023/04/17/advent-of-code-2022-day12.html) challenge to work out the
shortest distance between each valve pair and stored these distances in a map in each of the valve data structures.
I also added the distances to each valve to the starting valve in case it had a zero flow rate since we have to start
there.

Then I did a brute-force recursive search for all paths through the valves to find the one with the highest flow
rate.
The paths are pruned once we exceed the allowed time but apart from that there is no other pruning.

The result is the highest flow rate achievable.

```groovy
def TIME = 30
def rooms = stream(nextLine).map{ /Valve (.*) has.*rate=(\d+).*valves? (.*)$/n; [$1, $2, $3.split(/, */)] }
                            .collectEntries{ name,rate,tunnels -> [name, [name:name, rate:rate, tunnels:tunnels, dist:[:]]] }
def start = rooms['AA']

// Dijkstra's algorithm
def findDistance(start, end) {
   rooms.map{it[1]}.each{ it.fd = -1 }
   start.fd = 0
   for(def current = [start]; current.noneMatch{it == end} && current; ) {
      current = current.flatMap{ r -> r.reachable.filter{it.fd == -1}.map{ it.fd = r.fd+1; it } }
   }
   return end.fd
}

// Find all paths that do not exceed allowed time
def paths() { doPaths([current:start.name, time:0, visited:[:], order:[]]) }
def doPaths(path) {
   def d = rooms[path.current].dist
   def next = d.map{it[0]}.filter{ !path.visited[it] && path.time + d[it] + 1 < TIME }
                          .flatMap{ doPaths([current: it,
                                             visited: [(it):true]+ path.visited,
                                             time   : path.time + d[it] + 1,
                                             order  : path.order << it]) }
   next ? next : [path]
}

// Total flow is sum of rate per valve multiplied by remaining time from when valve is opened
def flow(valveOrder) {
   valveOrder.reduce([last:start.name, time:0, flow:0]){ o,it ->
      def t = o.time + rooms[o.last].dist[it] + 1
      [last:it, time:t, flow:o.flow + rooms[it].rate * (TIME - t)]
   }.flow
}

rooms.map{it[1]}.each{ room -> room.reachable = room.tunnels.map{ rooms[it] } }
def valves = rooms.map{it[1]}.filter{ it.rate > 0 }
valves.each{ v1 -> valves.filter{ it != v1 }.each{ v2 -> v1.dist[v2.name] = v2.dist[v1.name] = findDistance(v1,v2) } }
valves.each{ start.dist[it.name] = findDistance(start,it) }
def bestPath = paths().max{ flow(it.order) }
flow(bestPath.order)
```

## Part 2

For part 2 we are given an elephant friend to help us open the valves.
It will take 4 minutes to teach them how to open the valves, so now we only have 26 minutes in total.
We need to find the optimum flow rate achievable with two people now available to open the valves. 

To solve this, I decided to first determine the `bestFlow` for one person doing the work in 26 minutes.
Since there is not enough time to open all the valves in the amount of time, I took the remaining valves that had not
been opened and worked out how much additional flow we would have had if someone only opened those valves in 26 minutes.

This additional flow (which I called `remainingFlow`) we can then use when searching for pairs of paths.
We find all paths that have at least this `remainingFlow` and for each one of those paths we find the best flow for
whatever valves are left over (filtering out flows that are lower than the `remainingFlow`).

The reason we can use this threshold to prune our search is because  we already know that both paths in each pair
have to have a flow less than the best flow (by definition) and so if one of the two paths in a pair has a value less
than `remainingFlow` the two flows added together will be less than `bestFlow + remainingFlow` and we may as well just
return the `bestFlow + remainingFlow` as our answer.

Once we have found all path pairs which both have a flow of at least `remainingFlow` we just find the pair with the
highest combined value and this is our result.

```groovy
def TIME = 30 - 4
def rooms = stream(nextLine).map{ /Valve (.*) has.*rate=(\d+).*valves? (.*)$/n; [$1, $2, $3.split(/, */)] }
                            .collectEntries{ name,rate,tunnels -> [name, [name:name, rate:rate, tunnels:tunnels, distances:[:]]] }
def start = rooms['AA']

// Dijkstra's algorithm
def findDistance(start, end) {
   rooms.map{it[1]}.each{ it.fd = -1 }
   start.fd = 0
   for(def current = [start]; current.noneMatch{it == end} && current; ) {
      current = current.flatMap{ r -> r.reachable.filter{it.fd == -1}.map{ it.fd = r.fd+1; it } }
   }
   return end.fd
}

// Calculate total flow for given valve opening order
def flow(valveOrder) {
   valveOrder.reduce([last:start.name, time:0, flow:0]){ o,it ->
      def t = o.time + rooms[o.last].distances[it] + 1
      [last:it, time:t, flow:o.flow + rooms[it].rate * (TIME - t)]
   }.flow
}

rooms.map{it[1]}.each{ room -> room.reachable = room.tunnels.map{ rooms[it] } }
def valves = rooms.map{it[1]}.filter{ it.rate > 0 }
valves.each{ v1 -> valves.filter{ it != v1 }.each{ v2 -> v1.distances[v2.name] = v2.distances[v1.name] = findDistance(v1,v2) } }
valves.each{ start.distances[it.name] = findDistance(start,it) }

// Recursively find all paths that have at least given flow rate
def paths(nodes, minFlow) { doPaths([current:start.name, time:0, toVisit:nodes, order:[], flow:0], [value:minFlow]) }
def doPaths(path, minFlow) {
   def distances = rooms[path.current].distances
   def next = path.toVisit.map{it[0]}.filter{ path.time + distances[it] + 1 < TIME }
                  .flatMap{ def order = path.order << it
                            doPaths([current : it,
                                     time    : path.time + distances[it] + 1,
                                     toVisit : path.toVisit - [(it):true],
                                     order   : order,
                                     flow    : flow(order)], minFlow) }
                  .filter{ it.flow >= minFlow.value }
   next ? next : [path]
}

// Find best path based on maximising the total flow for one elf/elephant
def valvesMap = valves.collectEntries{ [it.name,true] } - ['AA']
def bestPath  = paths(valvesMap, 0).max{ flow(it.order) }

// Determine what additional flow is generated by finding optimal path amongst any remaining valves
def remainingBestPath = paths(valvesMap - bestPath.order, 0).max{ flow(it.order) }
def remainingFlow     = remainingBestPath ? flow(remainingBestPath.order) : 0

// The remainingFlow becomes our threshold when searching for path pairs since if either elf or elephant
// can't do better than this we can always revert back to using bestPath and remainingBestPath to get a
// higher overall flowRate
paths(valvesMap, remainingFlow).flatMap{ path ->
   paths(valvesMap - path.order, remainingFlow).map{ path.flow + it.flow }
}
.max()
```
