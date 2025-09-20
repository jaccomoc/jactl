---
title: Dependency
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

To use Jactl you will need to add a dependency on the Jactl library.

<Tabs>
<TabItem value="Gradle" label="Gradle" default>
In the `dependencies` section of your `build.gradle` file:
```groovy
implementation group: 'io.jactl', name: 'jactl', version: '2.3.0'
```
</TabItem>
<TabItem value="Maven" label="Maven">
In the `dependencies` section of your `pom.xml`:
```xml
<dependency>
 <groupId>io.jactl</groupId>
 <artifactId>jactl</artifactId>
 <version>2.3.0</version>
</dependency>
```
</TabItem>
</Tabs>

