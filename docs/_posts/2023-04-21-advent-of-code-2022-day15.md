---
layout:     post
title:      "Advent Of Code 2022 - Day 15"
date:       2023-04-21 09:48:45 +1000
categories: blog
author:     "James Crawford"
---

Continuing to solve the Advent of Code 2022 problems
(see [Advent of Code - Day 1]({{ site.baseurl }}{% link _posts/2023-04-06-advent-of-code-2022-day1.md %})).

Links:
* [Jactl Programming Language](https://jactl.io)
* [Jactl on Github](https://github.com/jaccomoc/jactl)

To run the example code in this post save the code into file such as `advent.jactl` and take your input from the
Advent of Code site (e.g. `advent.txt`) and run it like this:
```shell
$ cat advent.txt | java -jar jactl-{{ site.content.jactl_version }}.jar advent.jactl 
```

## Day 15: Beacon Exclusion Zone

See [Day 15](https://adventofcode.com/2022/day/15) for a detailed description of the problem.

### Part 1

Today's challenge was about sensors and beacons.
We are given input that identifies a number of sensors, where they are on a two-dimensional grid, and where the
nearest beacon to the sensor is (where distance is calculated using
[Manhattan or Taxicab distance](https://en.wikipedia.org/wiki/Taxicab_geometry)).
For example:
```
Sensor at x=2, y=18: closest beacon is at x=-2, y=15
Sensor at x=9, y=16: closest beacon is at x=10, y=16
Sensor at x=13, y=2: closest beacon is at x=15, y=3
Sensor at x=12, y=14: closest beacon is at x=10, y=16
```

For part 1 we need to find how many places on the grid line where `y=2000000` it is not possible for there to be a
beacon based on the knowledge of which beacons are closest to our sensors.

Since, for every sensor, we know the closest beacon, we can calculate the distance to that beacon and eliminate all
grid locations closer than that distance from being potential locations for any other beacon.
If we do that for all sensors we can work out all the locations where a beacon cannot be located.

For part 1, we only need to count the squares on the given row that cannot contain a beacon.

The way we read in the input and then iterate over the sensors, working out what interval on row `2000000` that
sensor precludes there being a beacon based on which squares on that row are closer to the sensor than its nearest
beacon.
Then we merge all the intervals, eliminate any beacons that we know are in that range and sum the size of the
intervals.

```groovy
def ROW = 2000000
def sensors = stream(nextLine).map{ /x=(-?\d+), y=(-?\d+).*x=(-?\d+), y=(-?\d+)/n; newSensor($1,$2,$3,$4) }
def newSensor(sx,sy,bx,by) { [[x:sx,y:sy], [x:bx,y:by], (bx-sx).abs()+(by-sy).abs(), (sy-ROW).abs()] }   // Note 1
def beacons = sensors.filter{ it[1].y == ROW }.map{ it[1].x }.sort().unique()                            // Note 2

def merge(a,i2) { def i1 = a[-1]; i2.p > i1.q ? a << i2 : a.subList(0,-1) << [p:i1.p, q:[i1.q,i2.q].max()] }
sensors.filter{ sens,b,dist,rowd -> dist >= rowd }                                                       // Note 3
       .map{ sens,b,dist,rowd -> [p:sens.x-(dist-rowd), q:sens.x+(dist-rowd)] }                          // Note 4
       .sort{ a,b -> a.p <=> b.p }                                                                       // Note 5
       .reduce([]){ a,it -> !a ? [it] : merge(a,it) }                                                    // Note 6
       .map{ (it.q - it.p + 1) - beacons.filter{ x -> x >= it.p && x <= it.q }.size() }                  // Note 7
       .sum()
```

Notes:
1. For each sensor we have a list of sensor coordinates, beacon coordinates, distance to beacon, and distance to the
row.
2. We pull out all the beacon locations into a separate list for later use.
3. We filter out sensors that are too far away from the line to contribute any information about beacons on that row.
4. We create an interval of the row `[p,q]` where, for that sensor, we know there cannot be a beacon.
5. We sort on the lower bound of the intervals to make it easy to merge overlapping intervals.
6. We iterate over the sorted intervals and if two consecutive intervals overlap we replace them with a new interval
that is the merge of the two.
7. We convert each interval into its size (number of locations) and remove any beacons that we already know about in
that interval.

### Part 2

Part 2 was a lot harder.
This time, rather than focussing on a single row, we need to work out from the input what single location in the entire
grid could have an unknown beacon located in it.
The only additional information given to us is that the x and y coordinates are no lower than `0` and no higher
than `4000000`.

I initially came up with a brute force approach where I iterated over the rows, determining the intervals as in part 1
but now, instead of wanting locations where we can't have a beacon, I reworked the algorithm to extract the gaps
between the intervals from part 1 to get all the locations where an unknown beacon might possibly reside.
This worked, but was a bit slow, so I wanted to see if there was a better way.

Since we are told that there is only a single location on the grid where our missing beacon could be, we know that
there must be at least one sensor for which the distance to the missing beacon is `d + 1` where `d` is the distance
to that sensor's closest beacon.
The reason is that if the distance to all sensors was less than their `d` value then we would already know about the
beacon, and if the distance to all sensors was greater than their `d + 1` value then there would be more than one
location where a missing beacon could reside.

With Manhattan distance, instead of having a circle that identifies points within a given radius or distance from
a sensor, you get a diamond shape (see [Day 15](https://adventofcode.com/2022/day/15)) for an example.
This means that we need only consider the points on the diamond at distance `d + 1` from each sensor when looking for
our missing beacon.

Again, I implemented this solution, but the number of points was so high that it ran in a similar amount of time to
the original brute force approach that I had already discarded for being too slow.

After some more consideration, it occurred to me that the location must be at the intersection of at least two of
the `d + 1` diamonds of the sensors.
Otherwise, there would be more than one location where missing beacons could reside.
To understand why, consider the immediate surrounding squares to the one where our missing beacon is going
to reside (ignoring diagonal neighbours since we are using Manhattan distance).
Since we only allow one square to have the missing beacon, the other squares must be right on a `d` distant boundary
for a sensor (since if they were closer, then that would put us within `d` of that sensor too).
But, if our neighbouring squares are on the boundary of a diamond distance `d` from a sensor, then by definition
we are on the `d + 1` diamond for that sensor.
Since it is not possible for all four neighbours to be on the `d` diamond for the same sensor there must be
more than one sensor for which we are on the `d + 1` diamond, and therefore we are at the intersection of at least
two such diamonds.

So, now we need only consider the intersection points of the diamonds of every pair of sensors when searching for
candidate locations.
The equation for the lines that bound the diamond are simple since the slope of the lines is always 1 or -1 (because
the lines are at 45 degrees).

The equations for the four lines of a diamond at distance `d` from a sensor at coordinates `[p,q]` are:
```
1. y = x - (p - d) + q
2. y = x - (p + d) + q
3. y = -x + (p - d) + q
4. y = -x + (p + d) + q
```

With two different diamonds we have 8 points of intersection to find and not 16 because 8 pairs are parallel and
so either don't intersect, or are the same line and not interesting for us.

For two diamonds we need to find the intersection points between lines 1 and 2 of each one combined with 3 and 4
of the other to get the 8 points of intersection since the other combinations are all parallel with each other.
So the eight combinations end up being:
```
1x3 1x4 2x3 2x4 3x1 3x2 4x1 4x2
```

To find the intersection points we take treat two equations as simultaneous equations and solve for `x` and `y`.
For example if we have equation 1 for diamond 1 and equation 3 for diamond 2:
```
1. y =  x - (p1 - d1) + q1
3. y = -x + (p2 - d2) + q2
```
Since `p1 - d1` is the `x` value for left most point of diamond 1 and `p2 - d2` is the `x` value for the left most
point of diamond 2, we will call these `dx1` and `dx2` for the moment to make the formulas slightly easier:
```
1. y =  x - dx1 + q1
3. y = -x + dx2 + q2

x - dx1 + q1 = -x + dx2 + q2
2x = dx1 - q1 + dx2 + q2
 x = (dx2 + q2 + dx1 - q1) / 2
Substituting x back into equation 1: 
 y = (dx2 + q2 + dx1 - q1) / 2 - dx1 + q1
   = (dx2 + q2 + dx1 - q1 - 2dx1 + 2q1) / 2
   = (dx2 + q2 - dx1 + q1) / 2  
```

As it turns out, we can use these two equations as the solution for `x` and `y` for all 4 combinations of equations 
and just vary the `dx1` and `dx2` values for each combination that we want by using:
```
dx1 = p1 - d1 and dx2 = p2 - d2
dx1 = p1 - d1 and dx2 = p2 + d2
dx1 = p1 + d1 and dx2 = p2 - d2
dx1 = p1 + d1 and dx2 = p2 + d2
``` 
Then we reverse the order of the diamonds to get the other four equations for our intersection points.

Don't forget, that the value of `d1` and `d2` we are using in our equations is actually one more than the distance
to the nearest beacon for that sensor.

Once we have found the location of the missing beacon we are supposed to return its `x` value multiplied by `4000000`
and added to its `y` value.

Putting it all together the code then looks like this:

```groovy
def sensors = stream(nextLine).map{ /x=(-?\d+), y=(-?\d+).*x=(-?\d+), y=(-?\d+)/n; [[x:$1,y:$2], ($1-$3).abs()+($2-$4).abs()] }
def dist(p1,p2) { (p1.x-p2.x).abs() + (p1.y-p2.y).abs() }                                    // Note 1
def intersect(s1,d1,s2,d2) { (ipts(s1.x, s1.y, d1, s2.x, s2.y, d2) + ipts(s2.x, s2.y, d2, s1.x, s1.y, d1)) }
def ipts(s1x, s1y, d1, s2x, s2y, d2) {                                                       // Note 2
   [[x1:s1x-d1,x2:s2x-d2], [x1:s1x-d1,x2:s2x+d2],
    [x1:s1x+d1,x2:s2x-d2], [x1:s1x+d1,x2:s2x+d2]].map{ [x:(it.x2+s2y+it.x1-s1y)/2, y:(it.x2+s2y-it.x1+s1y)/2] }
}
sensors.flatMap{ s1,d1 -> sensors.flatMap{ s2,d2 -> intersect(s1,d1+1,s2,d2+1) }             // Note 3
                                 .filter{ it.allMatch{ it[1] >= 0 && it[1] <= 4000000 } }    // Note 4
                                 .filter{ sensors.allMatch{ s,d -> dist(it,s) > d } } }      // Note 5
       .map{ it.x * 4000000L + it.y }[0]
```

Notes:
1. `dist()` function calculates Manhattan distance (sum of x difference plus y difference).
2. `insersect()` returns the 8 intersection points by invoking ipts() twice with sensors reversed the second time.
`ipts()` returns the four intersection points as described above.
3. We get the intersection points for each pair of sensors (includes sensor pairs where both are the same sensor
which evaluates to the 4 points of the diamond for that sensor). Even though we could exclude these pairs of duplicates
it doesn't really matter since we are just looking for candidate points that satisfy the next two criteria (in range,
and outside the beacon diamonds for all sensors). Note that we are passing in `d1+1` and `d2+1` since we want the
diamond that bounds the diamond based on beacon distance.
4. We make sure that the points are in range according to the requirements.
5. We make sure that the point sits outside all the beacon diamonds for each sensor.
