---
layout:     post
title:      "Advent Of Code 2022 - Day 19"
date:       2023-05-07 12:36:58 +1000
categories: blog
author:     "James Crawford"
---

# Day 19: Not Enough Minerals

See [Day 19](https://adventofcode.com/2022/day/19) for a detailed description of the problem.

> Continuing to solve the Advent of Code 2022 problems
> (see [Advent of Code - Day 1]({{ site.baseurl }}{% link _posts/2023-04-06-advent-of-code-2022-day1.md %})).
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

This was the hardest problem to solve so far.
This challenge was about building robots that can then mine for certain resources: ore, clay, obsidian, and geode.
Each resource type has a corresponding robot type that can mine for that type of resource and each robot can produce
one of its resource type per minute.

The robots each have a cost (number of resources) associated with building them (given by the blueprint) so the
challenge is to work out how to build enough robots of the right type so that at the end you have the maximum amount
of geode possible.

We are given 24 minutes and for each blueprint have to find the maximum geodes we can create, multiply this by the
blueprint id, and then sum the resulting values.

Every minute we have to decide what type of robot to build (we can only build one robot per minute) based on the
resources we have accumulated so far.
This means building the type of robot that leads to the maximum geodes in the remaining time left.
Sometimes it is better to build no robots than to build the wrong type of robot (to give us time to mine more
resources that could then let us build the type of robot we really want).

To solve this just by enumerating every possible choice for every minute is too costly as the search tree blows
out making this infeasible.
The solution is to introduce some heuristics to prune the search tree as much as possible.

In this case the heuristics used are:
* Search the option for building geode robots before searching options for building other robot types.
* If we have enough resources to build every type of robot then don't check the no-robot building option since this
is guaranteed to be a worse option.
* Keep track of the current best result and if the remaining time is not enough to exceed the current best result
assuming we could build one geode robot per minute (even if we don't actually have enough resources for that) then
don't bother searching this subtree any further.
* For each resource type we work out the maximum required for any robot type given by the blueprint and if we already
have enough robots of the right type to build that much in the next minute we don't bother building any more robots
of that type. This is because we can only consume resources when building a robot and we can only build one robot per
minute so the maximum of any resource we can consume is whatever is needed to build a robot. If we have enough robots
then every minute we will always have enough of that resource to build whatever type of robot we want so there is no
more need to build that robot type because we will never be able to consume more of that resource type anyway.

Since there are only four resource types and four robot types, we pack the resource counts and robot counts into
32-bit integers with each byte of the integer representing the resource count or robot count for that type.

```groovy
class BluePrint { int id, c_ore, c_clay, c_obs, c_geo }
int pack(int ore, int clay, int obs, int geo) { geo + (obs << 8) + (clay << 16) + (ore << 24) }

def bluePrints = stream(nextLine).filter{it}.map {
  /Blueprint (.*): .*costs (.*) ore.*costs (.*) ore.*costs (.*) ore and (.*) clay.*costs (.*) ore and (.*) obs/n;
  new BluePrint(id: $1, c_ore: pack($2,0,0,0), c_clay: pack($3,0,0,0),
                        c_obs: pack($4, $5,0,0),    c_geo:  pack($6,0, $7, 0))
}

// Extract individual resource counts
int geo(int resources)  { resources & 0xff }
int obs(int resources)  { (resources & 0xff00) >>> 8 }
int clay(int resources) { (resources & 0xff0000) >>> 16 }
int ore(int resources)  { (resources & 0x7f000000) >>> 24 }

// Maximum cost for each resource for building any robot
def findMaxCosts(b) { [0, obs(b.c_geo), [clay(b.c_geo),clay(b.c_obs)].max(), [ore(b.c_geo),ore(b.c_obs),ore(b.c_clay)].max()] as int[] }

int findMax(int time, BluePrint b, int[] maxResource) {
  //                Cost      Robot          Resource1             Resource2
  int[][] costs = [[b.c_geo,  pack(0,0,0,1), pack(0xff, 0, 0, 0), pack(0, 0, 0xff, 0)],
                   [b.c_obs,  pack(0,0,1,0), pack(0xff, 0, 0, 0), pack(0,0xff,0,0)],
                   [b.c_clay, pack(0,1,0,0), pack(0xff, 0, 0, 0), 0],
                   [b.c_ore,  pack(1,0,0,0), pack(0xff, 0, 0, 0), 0],
                   [0,        pack(0,0,0,0), 0,                   0]]  // entry for building no robots
  return doFindMax(costs, 0, pack(1,0,0,0), time, 0, findMaxCosts(b), maxResource)
}

int doFindMax(int[][] costs, int resources, int robots, int time, int maxGeo, int[] maxCosts, int[] maxResource) {
  if (time == 1) {
    resources += robots                                          // gather last lot of resources
    return geo(resources) > maxGeo ? geo(resources) : maxGeo      // return maximum count of geo seen so far
  }
  // if not enough time to build enough geo robots to catch up
  return 0 if maxGeo - (geo(resources) + geo(robots) * time) > maxResource[time]

  // Can build geo robot so we don't bother with any other types since the earlier we build geo robots the better
  return doFindMax(costs, resources + robots - costs[0][0], robots + costs[0][1], time - 1, maxGeo, maxCosts, maxResource) if canBuild(costs[0][0], resources, costs[0][2], costs[0][3])

  // Check for other robot types. If we build each other type then don't bother checking no-robot option since
  // this can never be a better option when we have enough resources for all non-geo robot types.
  for (int i = 1, built = 0; i < costs.size(); i++) {
    continue if     i < 4 && robotCount(i,robots) >= maxCosts[i]  // Already have enough robots of this type
    continue unless (i < 4 || built < 3) && canBuild(costs[i][0], resources, costs[i][2], costs[i][3])
    int geo = doFindMax(costs, resources + robots - costs[i][0], robots + costs[i][1], time - 1, maxGeo, maxCosts, maxResource)
    maxGeo = geo if geo > maxGeo
    built++
  }
  return maxGeo
}
int robotCount(int i, int robots) { (robots & (0xff<<(8*i))) >>> (8*i) }

// True if we have enough of resource1 and resource2 (which are the masks used to extract value from resources)
boolean canBuild(int cost, int resources, int resource1, int resource2) {
  return false if (cost & resource1) > (resources & resource1)
  return false if (cost & resource2) > (resources & resource2)
  return true
}

def TIME = 24
println bluePrints.map{ bluePrint -> findMax(TIME, bluePrint, (TIME+1).map{ it.sum() }) * bluePrint.id }
                  .sum()
```

## Part 2

For part 2 we now have 32 minutes to do our search, but we only need search the first 3 blueprints, and we need to
multiply the maximum geode values together.

We just replace the last 3 lines of the part 1 solution with this:

```groovy
def TIME = 32
println bluePrints.limit(3)
                  .map{ bluePrint -> findMax(TIME, bluePrint, (TIME+1).map{ it.sum() } as int[]) }
                  .reduce(1){p,it -> p*it}
```
