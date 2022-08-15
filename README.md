# dumbjshell

Trying to create a dumb shell that executes Java code. Like JShell. Basic idea is to create classes dynamically using ASM, 
and parse the Java expressions using JavaParser. It does not support much at the moment.

Example:

```shell
dumbjshell> 1
==> 1
dumbjshell> 1 + 20
==> 21
dumbjshell> int a = 123
==> 123
dumbjshell> int b = 3232
==> 3232
dumbjshell> a + b
==> 3355
dumbjshell> int c = a + b
==> 3355
dumbjshell> c = a = b = 100
==> 100
dumbjshell> a
==> 100
dumbjshell> b
==> 100
dumbjshell> c
==> 100
dumbjshell> exit
Exiting...
```